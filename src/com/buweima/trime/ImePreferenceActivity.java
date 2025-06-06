/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.buweima.trime;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.ListPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
//import android.util.Log;

/**
 * Manages IME preferences. 
 */
public class ImePreferenceActivity extends PreferenceActivity {

  private final String licenseUrl = "file:///android_asset/licensing.html";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.prefs);

    Preference license = findPreference("pref_licensing");
    license.setOnPreferenceClickListener(new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
        showLicenseDialog();
        return true;
      }
    });

    Preference importdb = findPreference("pref_importdb");
    importdb.setOnPreferenceClickListener(new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
        openImportDatabase();
        return true;
      }
    });

    Preference exportdb = findPreference("pref_exportdb");
    exportdb.setOnPreferenceClickListener(new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
        exportDatabase(DictionaryHelper.getExportName());
        return true;
      }
    });

    ListPreference candnum = (ListPreference)findPreference("pref_cand_num");
    candnum.setSummary(candnum.getEntry());
    candnum.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        ListPreference candnum = (ListPreference)preference;
        candnum.setValue(newValue.toString());
        candnum.setSummary(candnum.getEntry());
        return true;
      }
    });

    ListPreference candFontSize = (ListPreference)findPreference("pref_cand_font_size");
    candFontSize.setSummary(candFontSize.getValue());
    candFontSize.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        ((ListPreference)preference).setSummary(newValue.toString());
        return true;
      }
    });

    ListPreference keyFontSize = (ListPreference)findPreference("pref_key_font_size");
    keyFontSize.setSummary(keyFontSize.getValue());
    keyFontSize.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        ((ListPreference)preference).setSummary(newValue.toString());
        return true;
      }
    });

    ListPreference canMaxPhrase = (ListPreference)findPreference("pref_cand_max_phrase");
    canMaxPhrase.setSummary(canMaxPhrase.getEntry());
    canMaxPhrase.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        ListPreference canMaxPhrase = (ListPreference)preference;
        canMaxPhrase.setValue(newValue.toString());
        canMaxPhrase.setSummary(canMaxPhrase.getEntry());
        return true;
      }
    });
  }

  private void showLicenseDialog() {
	    View licenseView = View.inflate(this, R.layout.licensing, null);
	    WebView webView = (WebView) licenseView.findViewById(R.id.license_view);
	    webView.getSettings().setSupportZoom(true);//
	    webView.getSettings().setBuiltInZoomControls(true);//
	    webView.setWebViewClient(new WebViewClient() {
	      @Override
	      public boolean shouldOverrideUrlLoading(WebView view, String url) {
	        // Disable all links open from this web view.
	        return true;
	      }
	    });
	    webView.loadUrl(licenseUrl);

	    new AlertDialog.Builder(this)
	      .setTitle(R.string.ime_name)
	      .setView(licenseView)
	      .show();
	  }


  private void openImportDatabase() {
    final String[] files = DictionaryHelper.getImportNames();
    new AlertDialog.Builder(this)
      .setTitle(R.string.pref_importdb)
      .setItems(files,
      new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          importDatabase(files[id]);
          di.dismiss();
        }
      })
      .show();
  }

  private void importDatabase(String fn) {
    TRIME ime = TRIME.getService();
    if (ime != null) ime.importDatabase(fn);
    else new DictionaryHelper(this).importDatabase(fn);
  }

  private void exportDatabase(String fn) {
    TRIME ime = TRIME.getService();
    if (ime != null) ime.exportDatabase(fn);
    else new DictionaryHelper(this).exportDatabase(fn);
  }
}
