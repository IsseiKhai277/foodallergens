#include "llama/llama.h"
#include <vector>
#include <jni.h>
#include <string>
#include <cstring>
#include <algorithm>
#include <android/log.h>
#include <chrono>
#include <set>
#include <sstream>


#define LOG_TAG "SLM_NATIVE"

/*llama_batch make_batch(
        const std::vector<llama_token>& tokens,
        int n_ctx) {

    llama_batch batch = llama_batch_init(
            tokens.size(),
            0,
            n_ctx
    );

    for (int i = 0; i < tokens.size(); i++) {
        batch.token[i] = tokens[i];
        batch.pos[i]   = i;
        batch.seq_id[i][0] = 0;
        batch.n_seq_id[i]  = 1;
        batch.logits[i] = false;
    }

    batch.logits[tokens.size() - 1] = true;
    return batch;
}*/

/*static const std::set<std::string> ALLOWED_ALLERGENS = {
        "milk", "egg", "peanut", "tree nut",
        "wheat", "soy", "fish", "shellfish", "sesame"
};*/

std::string runModel(const std::string& prompt, const std::string& model_path) {

    // ================= Metrics =================
    auto t_start = std::chrono::high_resolution_clock::now();
    bool first_token_seen = false;

    long ttft_ms = -1;
    long itps = -1;
    long otps = -1;
    long oet_ms = -1;

    int generated_tokens = 0;

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "runModel() started with model: %s", model_path.c_str());

    // ================= Backend =================
    llama_backend_init();

    // ================= Load model =================
    llama_model_params model_params = llama_model_default_params();

    llama_model* model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (!model) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Failed to load model from: %s", model_path.c_str());
        return "TTFT_MS=0;ITPS=0;OTPS=0;OET_MS=0|ERROR_MODEL_LOAD_FAILED";
    }

    const llama_vocab* vocab = llama_model_get_vocab(model);

    // ================= Context =================
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 512;
    ctx_params.n_threads = 4;

    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Failed to create context");
        llama_free_model(model);
        return "TTFT_MS=0;ITPS=0;OTPS=0;OET_MS=0|ERROR_CONTEXT_INIT_FAILED";
    }

    // ================= Tokenize prompt =================
    std::vector<llama_token> prompt_tokens(prompt.size() + 8);

    int n_prompt = llama_tokenize(
            vocab,
            prompt.c_str(),
            prompt.size(),
            prompt_tokens.data(),
            prompt_tokens.size(),
            true,   // add BOS
            false
    );

    if (n_prompt <= 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Tokenization failed");
        llama_free(ctx);
        llama_free_model(model);
        return "TTFT_MS=0;ITPS=0;OTPS=0;OET_MS=0|ERROR_TOKENIZATION_FAILED";
    }

    prompt_tokens.resize(n_prompt);

    // ================= Initial batch (prompt) =================
    llama_batch batch = llama_batch_init(n_prompt, 0, ctx_params.n_ctx);
    batch.n_tokens = n_prompt;

    for (int i = 0; i < n_prompt; i++) {
        batch.token[i] = prompt_tokens[i];
        batch.pos[i]   = i;
        batch.seq_id[i][0] = 0;
        batch.n_seq_id[i]  = 1;
        batch.logits[i]    = false;
    }

    // 🔑 logits only on LAST prompt token
    batch.logits[n_prompt - 1] = true;

    // ================= Sampler =================
    llama_sampler* sampler = llama_sampler_init_greedy();

    // ================= Prefill =================
    auto t_prefill_start = std::chrono::high_resolution_clock::now();

    if (llama_decode(ctx, batch) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Prompt decode failed");
        llama_sampler_free(sampler);
        llama_free(ctx);
        llama_free_model(model);
        return "TTFT_MS=0;ITPS=0;OTPS=0;OET_MS=0|ERROR_DECODE_FAILED";
    }

    auto t_prefill_end = std::chrono::high_resolution_clock::now();
    long prefill_ms =
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    t_prefill_end - t_prefill_start
            ).count();

    if (prefill_ms > 0) {
        itps = (n_prompt * 1000L) / prefill_ms;
    }

    // ================= Generation =================
    std::string output;
    const int max_tokens = 64;

    int n_pos = 0;
    int n_predict = max_tokens;

    auto t_gen_start = std::chrono::high_resolution_clock::now();

    while (n_pos + batch.n_tokens < n_prompt + n_predict) {

        // ---- sample token (AFTER decode) ----
        llama_token token = llama_sampler_sample(sampler, ctx, -1);

        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        // ---- TTFT ----
        if (!first_token_seen) {
            auto t_first = std::chrono::high_resolution_clock::now();
            ttft_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                    t_first - t_start
            ).count();
            first_token_seen = true;
        }

        // ---- token → text ----
        char buf[128];
        int n = llama_token_to_piece(
                vocab, token, buf, sizeof(buf), 0, true);

        if (n > 0) {
            output.append(buf, n);
        }

        generated_tokens++;

        // ---- prepare next batch (REFERENCE CORRECT) ----
        batch = llama_batch_get_one(&token, 1);

        // ---- advance model ----
        if (llama_decode(ctx, batch) != 0) {
            break;
        }

        n_pos += batch.n_tokens;
    }

    auto t_gen_end = std::chrono::high_resolution_clock::now();
    long gen_ms =
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    t_gen_end - t_gen_start
            ).count();

    if (gen_ms > 0) {
        otps = (generated_tokens * 1000L) / gen_ms;
    }

    oet_ms = gen_ms;

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "Raw model output: %s", output.c_str());


    // allowed allergen set
    static const std::set<std::string> ALLOWED_ALLERGENS = {
            "milk","egg","peanut","tree nut",
            "wheat","soy","fish","shellfish","sesame"
    };

    // normalize to lowercase
    std::string output_lower = output;
    std::transform(output_lower.begin(), output_lower.end(), output_lower.begin(), ::tolower);

    // Check if output explicitly says EMPTY (case insensitive)
    if (output_lower.find("empty") != std::string::npos && 
        output_lower.find("not empty") == std::string::npos) {
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                            "Filtered output (Rule 2 applied): %s", "EMPTY");
        output = "EMPTY";
    } else {
        // Extract allergens by searching for each allowed allergen in the text
        std::vector<std::string> detected;
        std::set<std::string> detected_set; // to avoid duplicates
        
        for (const auto& allergen : ALLOWED_ALLERGENS) {
            // Check if allergen appears in the output
            if (output_lower.find(allergen) != std::string::npos) {
                if (detected_set.find(allergen) == detected_set.end()) {
                    detected.push_back(allergen);
                    detected_set.insert(allergen);
                }
            }
        }

        // rebuild output
        if (detected.empty()) {
            output = "EMPTY";
        } else {
            output.clear();
            for (size_t i = 0; i < detected.size(); i++) {
                if (i > 0) output += ",";
                output += detected[i];
            }
        }
    }

    __android_log_print(
            ANDROID_LOG_INFO,
            LOG_TAG,
            "Filtered output (Rule 2 applied): %s",
            output.c_str()
    );


    // ================= Cleanup =================
    llama_sampler_free(sampler);
    //llama_batch_free(batch);
    llama_free(ctx);
    llama_free_model(model);



    // ================= Return =================
    std::string result =
            "TTFT_MS=" + std::to_string(ttft_ms) +
            ";ITPS=" + std::to_string(itps) +
            ";OTPS=" + std::to_string(otps) +
            ";OET_MS=" + std::to_string(oet_ms) +
            "|" + output;

    return result;
}




extern "C"
JNIEXPORT jstring JNICALL
Java_edu_utem_ftmk_foodallergen_PredictionFragment_inferAllergens(
        JNIEnv *env,
        jobject,
        jstring inputPrompt,
        jstring modelPathJni) {

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "inferAllergens() called");

    // Extract model path from JNI
    const char *modelPathCstr = env->GetStringUTFChars(modelPathJni, nullptr);
    std::string modelPath(modelPathCstr);
    env->ReleaseStringUTFChars(modelPathJni, modelPathCstr);

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "Model path: %s", modelPath.c_str());

    // Extract prompt from JNI
    const char *cstr = env->GetStringUTFChars(inputPrompt, nullptr);
    std::string prompt(cstr);
    env->ReleaseStringUTFChars(inputPrompt, cstr);

    // Run model using EXACT prompt from Kotlin
    std::string output = runModel(prompt, modelPath);

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "Inference output: %s", output.c_str());

    return env->NewStringUTF(output.c_str());
}
