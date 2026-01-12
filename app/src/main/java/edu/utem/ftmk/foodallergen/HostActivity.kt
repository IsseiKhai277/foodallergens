package edu.utem.ftmk.foodallergen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * BITP 3453 Mobile Application Development
 * Semester 1 2025/2026
 *
 * @author Emaliana Kasmuri, FTMK, UTeM
 *
 * Purpose:
 * Host activity with bottom navigation for switching between
 * Prediction and Dashboard fragments.
 */
class HostActivity : AppCompatActivity() {

    companion object {
        // Load native libraries once when HostActivity is loaded
        init {
            System.loadLibrary("native-lib")
            System.loadLibrary("ggml-base")
            System.loadLibrary("ggml-cpu")
            System.loadLibrary("llama")
        }
    }

    private lateinit var bottomNavigation: BottomNavigationView
    private var predictionFragment: PredictionFragment? = null
    private var dashboardFragment: DashboardFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)

        bottomNavigation = findViewById(R.id.bottomNavigation)

        // Set up bottom navigation listener
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_prediction -> {
                    showFragment(getPredictionFragment())
                    true
                }
                R.id.nav_dashboard -> {
                    showFragment(getDashboardFragment())
                    true
                }
                else -> false
            }
        }

        // Show prediction fragment by default
        if (savedInstanceState == null) {
            showFragment(getPredictionFragment())
        }
    }

    private fun getPredictionFragment(): PredictionFragment {
        if (predictionFragment == null) {
            predictionFragment = PredictionFragment()
        }
        return predictionFragment!!
    }

    private fun getDashboardFragment(): DashboardFragment {
        if (dashboardFragment == null) {
            dashboardFragment = DashboardFragment()
        }
        return dashboardFragment!!
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /**
     * Refresh dashboard when switching from prediction
     */
    fun refreshDashboard() {
        dashboardFragment?.loadDashboardData()
    }
}
