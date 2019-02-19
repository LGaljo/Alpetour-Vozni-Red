package com.lukag.atvoznired;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.TextView;

import com.jude.swipbackhelper.SwipeBackHelper;
import com.lukag.atvoznired.Adapterji.AutoCompleteAdapter;
import com.lukag.atvoznired.Adapterji.priljubljenePostajeAdapter;
import com.lukag.atvoznired.Objekti.BuildConstants;
import com.lukag.atvoznired.UpravljanjeSPodatki.DataSourcee;
import com.lukag.atvoznired.UpravljanjeSPodatki.UpravljanjeSPriljubljenimi;
import com.lukag.atvoznired.UpravljanjeSPodatki.UpravljanjeZZadnjimiIskanimi;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String EXTRA_MESSAGE = "com.lukag.atvoznired";

    private AutoCompleteTextView vstopnaPostajaView;
    private AutoCompleteTextView izstopnaPostajaView;
    private Calendar calendarView;

    private DrawerLayout mDrawerLayout;

    public TextView koledar;
    public String datum;
    private NavigationView navigationView;

    private View contextView;

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeContainer;
    private priljubljenePostajeAdapter pAdapter;

    public static Runnable runs;
    public static Boolean sourcesFound = true;

    private UpravljanjeSPriljubljenimi favs;
    private DatePickerDialog.OnDateSetListener date;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PreferenceManager.setDefaultValues(this, R.xml.app_preferences, false);

        // Preverjanje, če se smo se vrnili nazaj zaradi napake v programu
        intentManager();

        // Nastavi SwipeBackHelper knjižnico
        SwipeBackHelper.onCreate(this);
        SwipeBackHelper.getCurrentPage(this).setSwipeBackEnable(false);
        SwipeBackHelper.getCurrentPage(this).setDisallowInterceptTouchEvent(true);

        // Poišče poglede in jih nastavi
        findViews();

        // Nastavi AutoCompleteTextView in mu pripne Custom Arrayadapter
        dodajAutoCompleteTextView();

        // Pripravi instanco koledarja za uporabo
        obNastavitviDatuma();

        // Nastavi layout in listener za klike
        handleNavigationMenu();

        // Prepreci odpiranje tipkovnice ob zagonu
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // V vnosna polja nastavim zadnje iskane
        UpravljanjeZZadnjimiIskanimi.nastaviZadnjiIskani(this, vstopnaPostajaView, izstopnaPostajaView, koledar);

        // Pripravim recyclerview za uporabo in nastavim instanco za uporabo SharedPref
        prikazPriljubljenihRecycler();

        // Pripravim SwipeContainer in njegove barve
        manageSwipeContainer();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        SwipeBackHelper.onPostCreate(this);
        pAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onStart() {
        super.onStart();
        koledar.setText(DataSourcee.pridobiCas("dd.MM.yyyy"));
        datum = DataSourcee.pridobiCas("yyyy-MM-dd");
        pAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        favs.shraniPriljubljene();
        SwipeBackHelper.onDestroy(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        navigationView.getMenu().getItem(0).setChecked(true);
        pAdapter.notifyDataSetChanged();
    }

    private void intentManager() {
        Intent intent = getIntent();
        String reason = intent.getStringExtra("reason");
        contextView = findViewById(R.id.priljubljene_text);

        if (reason != null && reason.equals("no_connection")) {
            Snackbar.make(contextView, R.string.no_connection, Snackbar.LENGTH_LONG).show();
        } else if (reason != null) {
            Snackbar.make(contextView, R.string.error, Snackbar.LENGTH_LONG).show();
        }
    }

    private void prikazPriljubljenihRecycler() {
        favs = UpravljanjeSPriljubljenimi.getInstance();
        favs.setContext(this);

        // Pripravi RecyclerView za prikaz priljubljenih relacij
        pAdapter = new priljubljenePostajeAdapter(UpravljanjeSPriljubljenimi.priljubljeneRelacije, this, vstopnaPostajaView, izstopnaPostajaView, favs, koledar);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
        recyclerView.setAdapter(pAdapter);
        checkForNewRides();

        recyclerView.post(runs);
    }

    private void manageSwipeContainer() {
        swipeContainer = findViewById(R.id.swipeContainer);
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                runs.run();
            }
        });
        // Configure the refreshing colors
        swipeContainer.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
    }

    private void handleNavigationMenu() {
        mDrawerLayout = findViewById(R.id.drawer_layout);

        navigationView.getMenu().getItem(0).setChecked(true);
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                        int id = menuItem.getItemId();
                        switch (id) {
                            case R.id.first_screen:
                                break;
                            case R.id.nav_info:
                                goToAppInfo();
                                break;
                            case R.id.nav_settings:
                                goToSettings();
                                break;
                            default:
                                break;
                        }
                        menuItem.setChecked(false);
                        // close drawer when item is tapped
                        mDrawerLayout.closeDrawers();
                        return true;
                    }
                });
    }

    /**
     * Metoda usmerja odzive na klike v tem pogledu
     * @param v - View
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.delete_vp:
                // Izbrisi tekst v vnosu vstopne postaje
                vstopnaPostajaView.setText("", false);
                break;
            case R.id.delete_ip:
                // Izbrisi tekst v vnosu izstopne postaje
                izstopnaPostajaView.setText("", false);
                break;
            case R.id.submit:
                // Gumb za iskanje urnika
                UpravljanjeZZadnjimiIskanimi.shraniZadnjiIskani(MainActivity.this, vstopnaPostajaView, izstopnaPostajaView, koledar.getText().toString());
                preveriParametre();
                break;
            case R.id.swap:
                // Gumb za zamenjavo postajalisc
                String tmp = izstopnaPostajaView.getText().toString();
                izstopnaPostajaView.setText(vstopnaPostajaView.getText(), false);
                vstopnaPostajaView.setText(tmp, false);
                UpravljanjeZZadnjimiIskanimi.shraniZadnjiIskani(MainActivity.this, vstopnaPostajaView, izstopnaPostajaView, koledar.getText().toString());
                break;
            case R.id.textCalendar:
                // Pokazi koledar
                new DatePickerDialog(MainActivity.this, date, calendarView.get(Calendar.YEAR), calendarView.get(Calendar.MONTH), calendarView.get(Calendar.DAY_OF_MONTH)).show();
                break;
            default:
                break;
        }
    }

    /**
     * Metoda pripravi AutoCompleteTextView za uporabo
     */
    private void dodajAutoCompleteTextView() {
        AutoCompleteAdapter aca = new AutoCompleteAdapter(this, R.layout.autocomplete_list_item);
        vstopnaPostajaView.setDropDownBackgroundDrawable(this.getResources().getDrawable(R.drawable.autocomplete_dropdown));
        izstopnaPostajaView.setDropDownBackgroundDrawable(this.getResources().getDrawable(R.drawable.autocomplete_dropdown));

        vstopnaPostajaView.setThreshold(2);
        vstopnaPostajaView.setAdapter(aca);
        izstopnaPostajaView.setThreshold(2);
        izstopnaPostajaView.setAdapter(aca);

        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) vstopnaPostajaView.getLayoutParams();

        vstopnaPostajaView.setDropDownWidth(screenWidth() - (lp.leftMargin + lp.rightMargin));
        izstopnaPostajaView.setDropDownWidth(screenWidth() - (lp.leftMargin + lp.rightMargin));
    }

    /**
     * Metoda vrne sirino zaslona
     */
    private Integer screenWidth() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        return size.x;
    }

    /**
     * Metoda pripravi koledar
     */
    private void obNastavitviDatuma() {
        calendarView = Calendar.getInstance();

        date = new DatePickerDialog.OnDateSetListener() {
            String format = "dd.MM.yyyy";
            String formatApi = "yyyy-MM-dd";
            SimpleDateFormat textBoxFormat = new SimpleDateFormat(format, Locale.GERMAN);
            SimpleDateFormat ApiFormat = new SimpleDateFormat(formatApi, Locale.GERMAN);

            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                calendarView.set(Calendar.YEAR, year);
                calendarView.set(Calendar.MONTH, month);
                calendarView.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                koledar.setText(textBoxFormat.format(calendarView.getTime()));
                datum = ApiFormat.format(calendarView.getTime());
            }
        };
    }

    /**
     * V display message activity layoutu poisce iskane objekte
     */
    private void findViews() {
        vstopnaPostajaView = (AutoCompleteTextView) findViewById(R.id.vstopna_text);
        izstopnaPostajaView = (AutoCompleteTextView) findViewById(R.id.izstopna_text);
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view_pogled_priljubljenih);
        koledar = (TextView) findViewById(R.id.textCalendar);
        Button submit = (Button) findViewById(R.id.submit);
        ImageView delete_vp = (ImageView) findViewById(R.id.delete_vp);
        ImageView delete_ip = (ImageView) findViewById(R.id.delete_ip);
        ImageView swap = (ImageView) findViewById(R.id.swap);
        navigationView = findViewById(R.id.nav_view);

        koledar.setOnClickListener(this);
        submit.setOnClickListener(this);
        delete_vp.setOnClickListener(this);
        delete_ip.setOnClickListener(this);
        swap.setOnClickListener(this);
    }

    /**
     * Metoda preveri pravilnost vnosa podatkov in
     * sestavi ArrayList za prenos podatkov
     */
    private void preveriParametre() {
        ArrayList<String> prenos = new ArrayList<>();

        String vstopnaPostaja = vstopnaPostajaView.getText().toString();
        String izstopnaPostaja = izstopnaPostajaView.getText().toString();
        String vstopnaID = BuildConstants.seznamPostaj.get(vstopnaPostaja);
        String izstopnaID = BuildConstants.seznamPostaj.get(izstopnaPostaja);

        if (vstopnaPostaja.equals(izstopnaPostaja) || vstopnaPostaja.equals("") || izstopnaPostaja.equals("")) {
            Snackbar.make(contextView, R.string.invalid_search, Snackbar.LENGTH_LONG).show();
        } else {
            prenos.add(vstopnaID);
            prenos.add(vstopnaPostaja);
            prenos.add(izstopnaID);
            prenos.add(izstopnaPostaja);
            prenos.add(datum);
            submit(prenos);
        }
    }

    /**
     * Metoda preide v nov Intent -> Display_Schedule_Activity
     * @param prenos - arraylist potrebnih parametrov za klic post zahteve
     */
    private void submit(ArrayList<String> prenos) {
        Intent intent = new Intent(MainActivity.this, Display_Schedule_Activity.class);
        intent.putStringArrayListExtra(EXTRA_MESSAGE, prenos);
        startActivity(intent);
    }

    private void goToAppInfo() {
        Intent apinfointent = new Intent(this, DisplayAppInfo.class);
        startActivity(apinfointent);
    }

     private void goToSettings() {
        Intent gotosettings = new Intent(this, SettingsActivity.class);
        startActivity(gotosettings);
    }

    private void checkForNewRides() {
        runs = new Runnable() {
            public void run() {
                // a potentially time consuming task
                while (sourcesFound) {
                    // wait
                }
                DataSourcee.findNextRides(MainActivity.this, pAdapter);
                swipeContainer.setRefreshing(false);
            }
        };
    }
}
