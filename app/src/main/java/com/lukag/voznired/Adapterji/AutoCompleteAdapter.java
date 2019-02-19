package com.lukag.voznired.Adapterji;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import com.android.volley.Request;
import com.lukag.voznired.Objekti.BuildConstants;
import com.lukag.voznired.SettingsActivity;
import com.lukag.voznired.UpravljanjeSPodatki.DataSourcee;
import com.lukag.voznired.UpravljanjeSPodatki.VolleyTool;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import static com.lukag.voznired.Objekti.BuildConstants.seznamPostaj;

public class AutoCompleteAdapter extends ArrayAdapter<String> implements Filterable {
    private ArrayList<String> seznamImenPostaj;
    private ArrayList<String> suggestions;
    private Context context;
    private Boolean sumniki_pref;
    private SharedPreferences sp;

    public AutoCompleteAdapter(@NonNull Context context, @LayoutRes int resource) {
        super(context, resource);
        this.seznamImenPostaj = new ArrayList<>();
        this.suggestions = new ArrayList<>();
        this.context = context;
        this.sp = PreferenceManager.getDefaultSharedPreferences(context);
        pridobiSeznam();
    }

    /**
     * To je metoda, ki z API klicem pridobi seznam vseh postaj in jih shrani v adapter
     */
    private void pridobiSeznam() {
        try {
            String timestamp = DataSourcee.pridobiCas("yyyyMMddHHmmss");
            String token = DataSourcee.md5(BuildConstants.tokenKey + timestamp);

            VolleyTool vt = new VolleyTool(context, "https://prometWS.alpetour.si/WS_ArrivaSLO_TimeTable_DepartureStations.aspx");

            vt.addParam("cTimeStamp", timestamp);
            vt.addParam("cToken", token);
            vt.addParam("json", "1");

            vt.executeRequest(Request.Method.POST, new VolleyTool.VolleyCallback() {

                @Override
                public void getResponse(String response) {
                    try {
                        JSONObject responseObj = new JSONArray(response).getJSONObject(0);
                        int napakaID = responseObj.getInt("Error");

                        if (napakaID != 0) {
                            String napakaMessage = responseObj.getString("ErrorMsg");
                            Log.e("API", napakaMessage);
                        }

                        JSONArray postaje = responseObj.getJSONArray("DepartureStations");

                        if (postaje != null) {
                            for (int i = 0; i < postaje.length(); i++) {
                                JSONObject postaja = postaje.getJSONObject(i);
                                String idPostaje = postaja.getString("JPOS_IJPP");
                                String imePostaje = postaja.getString("POS_NAZ");
                                seznamPostaj.put(imePostaje, idPostaje);
                                seznamImenPostaj.add(imePostaje);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * To je custom filter, s katerim lahko prosto filtram seznam
     *  Omogoča primerjanje besede s šumniki in enako besedo brez šumnikov
     */
    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {

            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                if (constraint != null) {

                    suggestions.clear();
                    sumniki_pref = sp.getBoolean (SettingsActivity.ISKANJE_S_SUMNIKI, false);

                    for (String str : seznamImenPostaj) {
                        // Odstrani šumnike za uporabnike, ki jih ne uporabljajo
                        String str1;
                        String str2;
                        if (sumniki_pref) {
                            str1 = DataSourcee.odstraniSumnike(str.toLowerCase());
                            str2 = DataSourcee.odstraniSumnike(constraint.toString().toLowerCase());
                        } else {
                            str1 = str.toLowerCase();
                            str2 = constraint.toString().toLowerCase();
                        }

                        if (str1.startsWith(str2)) {
                            suggestions.add(str);
                        }
                    }

                    results.values = suggestions;
                    results.count = suggestions.size();
                    return results;
                } else {
                    return new FilterResults();
                }
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {

                if (results != null && results.count > 0) {
                    clear();
                    ArrayList<String> filteredList = (ArrayList<String>) results.values;
                    for (String str : filteredList) {
                        add(str);
                    }
                } else {
                    //addAll(seznamImenPostaj);
                    clear();
                }
                notifyDataSetChanged();
            }
        };
    }
}