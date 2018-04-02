/*
 * Created by Brian Lau on 2018-03-20
 * Copyright (c) 2018. All rights reserved.
 *
 * Last modified: 2018-03-28
 */

package com.justbnutz.dockorientationrotatorlator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

public class ActivityRotatorlator extends AppCompatActivity {

    public static final String PACKAGE_NAME = "com.justbnutz.dockorientationrotatorlator";

    SharedPreferences mSharedPrefs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Init the layout
        setContentView(R.layout.activity_rotatorlator);

        // Load the Configurator Fragment into the view
        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.frame_fragment_container,
                        FragmentRotatorlatorConfigurator.newInstance(),
                        FragmentRotatorlatorConfigurator.TAG
                )
                .commit();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_options, menu);
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Make sure the checked item matches the preference
        menu.findItem(R.id.mnu_show_wireless_state).setChecked(
                mSharedPrefs.getBoolean(getString(R.string.prefkey_show_wireless_options), true)
        );
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.mnu_licences:
                // Launch licences Activity
                startActivity(
                        new Intent(this, OssLicensesMenuActivity.class)
                );
                return true;

            case R.id.mnu_open_source:
                runLink(getString(R.string.url_open_source));
                return true;

            case R.id.mnu_play_store:
                runLink(getString(R.string.url_play_store));
                return true;

            case R.id.mnu_contact:
                runLink(getString(R.string.url_tweet));
                return true;

            case R.id.mnu_show_wireless_state:
                // Toggle the Wireless Charging option visibility
                toggleWirelessPanel();
                return true;

            case R.id.mnu_show_test_panel:
                // Launch Test Panel Activity
                startActivity(
                        new Intent(this, ActivityTestPanel.class)
                );
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /**
     * Launch an internet link
     */
    private void runLink(String webUrl) {

        Intent browserIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(webUrl)
        );

        startActivity(browserIntent);
    }


    /**
     * Update the "Show Wireless Option" preference
     */
    private void toggleWirelessPanel() {
        // Invert the preference
        boolean showPanel = !mSharedPrefs.getBoolean(getString(R.string.prefkey_show_wireless_options), true);

        SharedPreferences.Editor updatePrefs = mSharedPrefs.edit();
        updatePrefs.putBoolean(getString(R.string.prefkey_show_wireless_options), showPanel);

        // If we're disabling the panel, set the Wireless Charging option to "No Change" as well
        if (showPanel) {
            updatePrefs.putInt(getString(R.string.prefkey_set_autorotate_wireless), 0);
        }

        // Commit the change
        updatePrefs.apply();

        // GUI update will be handled in the OnSharedPreferenceChangeListener in the Configurator Fragment
    }
}
