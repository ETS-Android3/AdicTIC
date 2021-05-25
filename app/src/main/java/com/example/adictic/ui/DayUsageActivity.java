package com.example.adictic.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adictic.R;
import com.example.adictic.entity.AppInfo;
import com.example.adictic.entity.AppUsage;
import com.example.adictic.entity.GeneralUsage;
import com.example.adictic.entity.YearEntity;
import com.example.adictic.rest.TodoApi;
import com.example.adictic.util.Constants;
import com.example.adictic.util.Funcions;
import com.example.adictic.util.TodoApp;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DayUsageActivity extends AppCompatActivity {
    // Constants defining order for display order
    private static final int _DISPLAY_ORDER_USAGE_TIME = 0;
    private static final int _DISPLAY_ORDER_LAST_TIME_USED = 1;
    private static final int _DISPLAY_ORDER_APP_NAME = 2;

    private TodoApi mTodoService;

    private final String TAG = "DayUsageActivity";
    private long idChild;
    private Chip CH_singleDate;
    private Spinner SP_sort;
    private TextView TV_initialDate;
    private TextView TV_finalDate;
    private TextView TV_error;
    private Button BT_initialDate;
    private Button BT_finalDate;
    private int initialDay;
    private int initialMonth;
    private int initialYear;
    private int finalDay;
    private int finalMonth;
    private int finalYear;
    private RecyclerView listView;
    private Map<Integer, Map<Integer, List<Integer>>> daysMap;
    private List<Integer> yearList;
    private List<Integer> monthList;
    private int xDays;
    private UsageStatsAdapter mAdapter;
    private final DatePickerDialog.OnDateSetListener initialDateListener = new DatePickerDialog.OnDateSetListener() {
        @Override
        public void onDateSet(DatePicker arg0, int year, int month, int day) {
            initialYear = year;
            initialMonth = month;
            initialDay = day;

            if (CH_singleDate.isChecked()) {
                finalYear = year;
                finalMonth = month;
                finalDay = day;
            } else checkFutureDates();

            getStats();

            BT_initialDate.setText(getResources().getString(R.string.date_format, initialDay, getResources().getStringArray(R.array.month_names)[initialMonth + 1], initialYear));
        }
    };
    private final DatePickerDialog.OnDateSetListener finalDateListener = new DatePickerDialog.OnDateSetListener() {
        @Override
        public void onDateSet(DatePicker arg0, int year, int month, int day) {
            finalYear = year;
            finalMonth = month;
            finalDay = day;

            getStats();

            BT_finalDate.setText(getResources().getString(R.string.date_format, finalDay, getResources().getStringArray(R.array.month_names)[finalMonth + 1], finalYear));
        }
    };

    private void setSpinner(){
        // Creem la llista dels elements
        List<String> spinnerArray = new ArrayList<>();
        spinnerArray.add(getString(R.string.time_span));
        spinnerArray.add(getString(R.string.last_time_used));
        spinnerArray.add(getString(R.string.alfabeticament));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.support_simple_spinner_dropdown_item,
                spinnerArray);

        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        SP_sort.setAdapter(adapter);

        SP_sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String selected = SP_sort.getSelectedItem().toString();
                if(selected.equals(getString(R.string.time_span)))
                    mAdapter.sortList(_DISPLAY_ORDER_USAGE_TIME);
                else if(selected.equals(getString(R.string.last_time_used)))
                    mAdapter.sortList(_DISPLAY_ORDER_LAST_TIME_USED);
                else
                    mAdapter.sortList(_DISPLAY_ORDER_APP_NAME);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        SP_sort.setSelection(0);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.usage_stats_layout);
        mTodoService = ((TodoApp) getApplication()).getAPI();

        listView = findViewById(R.id.pkg_list);

        SP_sort = findViewById(R.id.typeSpinner);

        CH_singleDate = findViewById(R.id.CH_singleDate);

        TV_initialDate = findViewById(R.id.TV_initialDate);
        TV_finalDate = findViewById(R.id.TV_finalDate);

        TV_error = findViewById(R.id.TV_emptyList);
        TV_error.setVisibility(View.GONE);

        BT_initialDate = findViewById(R.id.BT_initialDate);
        BT_initialDate.setOnClickListener(view -> btnInitialDate());
        BT_finalDate = findViewById(R.id.BT_finalDate);
        BT_finalDate.setOnClickListener(view -> btnFinalDate());

        ChipGroup chipGroup = findViewById(R.id.CG_dateChips);

        daysMap = new HashMap<>();
        yearList = new ArrayList<>();
        monthList = new ArrayList<>();

        idChild = getIntent().getLongExtra("idChild", -1);

        int day = getIntent().getIntExtra("day", -1);
        if (day == -1) {
            Calendar cal = Calendar.getInstance();
            finalDay = initialDay = cal.get(Calendar.DAY_OF_MONTH);
            finalMonth = initialMonth = cal.get(Calendar.MONTH);
            finalYear = initialYear = cal.get(Calendar.YEAR);
        } else {
            finalDay = initialDay = day;
            finalMonth = initialMonth = getIntent().getIntExtra("month", Calendar.getInstance().get(Calendar.MONTH));
            finalYear = initialYear = getIntent().getIntExtra("year", Calendar.getInstance().get(Calendar.YEAR));
        }

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (CH_singleDate.isChecked()) {
                BT_finalDate.setVisibility(View.INVISIBLE);
                TV_finalDate.setVisibility(View.INVISIBLE);
                TV_initialDate.setText(getResources().getString(R.string.date));
                BT_initialDate.setText(getResources().getString(R.string.date_format, initialDay, getResources().getStringArray(R.array.month_names)[initialMonth + 1], initialYear));

                if(initialDay != finalDay || initialMonth != finalMonth || initialYear != finalYear) {
                    finalDay = initialDay;
                    finalMonth = initialMonth;
                    finalYear = initialYear;
                    getStats();
                }

            } else {
                BT_finalDate.setVisibility(View.VISIBLE);
                TV_finalDate.setVisibility(View.VISIBLE);
                TV_initialDate.setText(getResources().getString(R.string.initial_date));
                BT_initialDate.setText(getResources().getString(R.string.date_format, initialDay, getResources().getStringArray(R.array.month_names)[initialMonth + 1], initialYear));
                BT_finalDate.setText(getResources().getString(R.string.date_format, finalDay, getResources().getStringArray(R.array.month_names)[finalMonth + 1], finalYear));
            }
        });

        chipGroup.setSelectionRequired(false);
        chipGroup.clearCheck();
        chipGroup.check(CH_singleDate.getId());
        chipGroup.setSelectionRequired(true);

        getMonthYearLists();
    }

    private void getStats() {
        checkFutureDates();
        String initialDate = getResources().getString(R.string.informal_date_format, initialDay, initialMonth + 1, initialYear);
        String finalDate = getResources().getString(R.string.informal_date_format, finalDay, finalMonth + 1, finalYear);

        Call<Collection<GeneralUsage>> call = mTodoService.getGenericAppUsage(idChild, initialDate, finalDate);

        call.enqueue(new Callback<Collection<GeneralUsage>>() {
            @Override
            public void onResponse(@NonNull Call<Collection<GeneralUsage>> call, @NonNull Response<Collection<GeneralUsage>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Collection<GeneralUsage> generalUsages = response.body();
                    Funcions.canviarMesosDeServidor(generalUsages);
                    makeList(generalUsages);
                } else {
                    showError();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Collection<GeneralUsage>> call, @NonNull Throwable t) {
                showError();
            }
        });
    }

    private void makeList(Collection<GeneralUsage> gul) {
        xDays = gul.size();
        long totalTime = 0;

        // Si hi ha diferents dies amb les mateixes aplicacions, sumem els temps
        List<AppUsage> appList = new ArrayList<>();
        for (GeneralUsage gu : gul) {
            for (AppUsage au : gu.usage) {
                int index = appList.indexOf(au);

                totalTime += au.totalTime;

                if (index != -1) {
                    AppUsage current = appList.remove(index);
                    AppUsage res = new AppUsage();
                    res.app = new AppInfo();
                    res.app.appName = au.app.appName;
                    res.app.pkgName = au.app.pkgName;
                    res.totalTime = au.totalTime + current.totalTime;
                    if (current.lastTimeUsed > au.lastTimeUsed)
                        res.lastTimeUsed = current.lastTimeUsed;
                    else
                        res.lastTimeUsed = au.lastTimeUsed;

                    appList.add(res);
                } else {
                    appList.add(au);
                }
            }
        }

        // Actualitzem el TV
        updateTotalTimeTV(totalTime);

        mAdapter = new UsageStatsAdapter(appList, DayUsageActivity.this);
        listView.setAdapter(mAdapter);
        //setSpinner ha d'anar després de l'adapter
        setSpinner();
    }

    private void updateTotalTimeTV(long totalTime) {
        TextView TV_totalUse = findViewById(R.id.TV_totalUseVar);

        // Set colours according to total time spent
        if (totalTime <= xDays * Constants.CORRECT_USAGE_DAY)
            TV_totalUse.setTextColor(getColor(R.color.colorPrimary));
        else if (totalTime > xDays * Constants.DANGEROUS_USAGE_DAY)
            TV_totalUse.setTextColor(Color.RED);
        else
            TV_totalUse.setTextColor(Color.rgb(255, 128, 64));

        // Canviar format de HH:mm:ss a "Dies Hores Minuts"
        long elapsedDays = totalTime / Constants.TOTAL_MILLIS_IN_DAY;
        totalTime %= Constants.TOTAL_MILLIS_IN_DAY;

        long elapsedHours = totalTime / Constants.HOUR_IN_MILLIS;
        totalTime %= Constants.HOUR_IN_MILLIS;

        long elapsedMinutes = totalTime / (60*1000);

        String text;
        if (elapsedDays == 0) {
            if (elapsedHours == 0) {
                text = elapsedMinutes + getString(R.string.minutes);
            } else {
                text = elapsedHours + getString(R.string.hours) + elapsedMinutes + getString(R.string.minutes);
            }
        } else {
            text = elapsedDays + getString(R.string.days) + elapsedHours + getString(R.string.hours) + elapsedMinutes + getString(R.string.minutes);
        }
        TV_totalUse.setText(text);
    }

    public void btnInitialDate() {
        DatePickerDialog initialPicker = new DatePickerDialog(this, R.style.datePicker, initialDateListener, initialYear, initialMonth, initialDay);
        initialPicker.getDatePicker().setMaxDate(Calendar.getInstance().getTimeInMillis());

        int firstYear = Collections.min(yearList);
        int firstMonth = Collections.min(daysMap.get(firstYear).keySet());
        List<Integer> month = daysMap.get(firstYear).get(firstMonth);
        int firstDay = Collections.min(month);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, firstDay);
        cal.set(Calendar.MONTH, firstMonth);
        cal.set(Calendar.YEAR, firstYear);

        initialPicker.getDatePicker().setMinDate(cal.getTimeInMillis());
        initialPicker.show();
    }

    public void btnFinalDate() {
        DatePickerDialog finalPicker = new DatePickerDialog(this, R.style.datePicker, finalDateListener, finalYear, finalMonth, finalDay);
        finalPicker.getDatePicker().setMaxDate(Calendar.getInstance().getTimeInMillis());

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, initialDay);
        cal.set(Calendar.MONTH, initialMonth);
        cal.set(Calendar.YEAR, initialYear);

        finalPicker.getDatePicker().setMinDate(cal.getTimeInMillis());

        finalPicker.show();
    }

    private void checkFutureDates() {
        if (initialYear > finalYear) {
            finalYear = initialYear;
            finalMonth = initialMonth;
            finalDay = initialDay;

            BT_finalDate.setText(getResources().getString(R.string.date_format, finalDay, getResources().getStringArray(R.array.month_names)[finalMonth + 1], finalYear));
        } else if (initialYear == finalYear) {
            if (initialMonth > finalMonth) {
                finalMonth = initialMonth;
                finalDay = initialDay;

                BT_finalDate.setText(getResources().getString(R.string.date_format, finalDay, getResources().getStringArray(R.array.month_names)[finalMonth + 1], finalYear));
            } else if (initialMonth == finalMonth) {
                if (initialDay > finalDay) {
                    finalDay = initialDay;

                    BT_finalDate.setText(getResources().getString(R.string.date_format, finalDay, getResources().getStringArray(R.array.month_names)[finalMonth + 1], finalYear));
                }
            }
        }
    }

    public void getMonthYearLists() {
        Call<List<YearEntity>> call = mTodoService.getDaysWithData(idChild);

        call.enqueue(new Callback<List<YearEntity>>() {
            @Override
            public void onResponse(@NonNull Call<List<YearEntity>> call, @NonNull Response<List<YearEntity>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    /* Agafem les dades de response i convertim en map **/
                    List<YearEntity> yEntityList = response.body();
                    Funcions.canviarMesosDeServidor(yEntityList);
                    if (yEntityList.isEmpty()) showError();
                    else {
                        daysMap = Funcions.convertYearEntityToMap(yEntityList);

                        yearList.addAll(daysMap.keySet());
                        yearList.sort(Collections.reverseOrder());

                        monthList.addAll(daysMap.get(yearList.get(0)).keySet());
                        monthList.sort(Collections.reverseOrder());

                        getStats();
                    }
                } else {
                    showError();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<YearEntity>> call, @NonNull Throwable t) {
                showError();
            }
        });
    }

    private void showError() {
        TV_error.setVisibility(View.VISIBLE);
        TV_error.setTextColor(Color.RED);
    }

    public static class AppNameComparator implements Comparator<AppUsage> {
        @Override
        public final int compare(AppUsage a, AppUsage b) {
            return a.app.appName.compareTo(b.app.appName);
        }
    }

    public static class LastTimeUsedComparator implements Comparator<AppUsage> {
        @Override
        public final int compare(AppUsage a, AppUsage b) {
            // return by descending order
            return (int) (b.lastTimeUsed - a.lastTimeUsed);
        }
    }

    public static class UsageTimeComparator implements Comparator<AppUsage> {
        @Override
        public final int compare(AppUsage a, AppUsage b) {
            return (int) (b.totalTime - a.totalTime);
        }
    }

    class UsageStatsAdapter extends RecyclerView.Adapter<UsageStatsAdapter.MyViewHolder> {
        private final LastTimeUsedComparator mLastTimeUsedComparator = new LastTimeUsedComparator();
        private final UsageTimeComparator mUsageTimeComparator = new UsageTimeComparator();
        private final AppNameComparator mAppLabelComparator = new AppNameComparator();
        private final ArrayList<AppUsage> mPackageStats;
        private final Context mContext;
        //private final ArrayMap<String, Drawable> mIcons = new ArrayMap<>();
        private int mDisplayOrder = _DISPLAY_ORDER_USAGE_TIME;
        private final LayoutInflater mInflater;

        UsageStatsAdapter(List<AppUsage> appList, Context c) {
            mContext = c;
            mInflater = LayoutInflater.from(c);
            mPackageStats = new ArrayList<>(appList);

            // Sort list
            sortList(_DISPLAY_ORDER_USAGE_TIME);
        }

        @NonNull
        @Override
        public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = mInflater.inflate(R.layout.usage_stats_item, parent,false);

            return new MyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
            AppUsage pkgStats = mPackageStats.get(position);
            if (pkgStats != null) {
                Funcions.setIconDrawable(mContext, pkgStats.app.pkgName, holder.icon);
                String label = pkgStats.app.appName;
                holder.pkgName.setText(label);
                holder.lastTimeUsed.setText(DateUtils.formatSameDayTime(pkgStats.lastTimeUsed,
                        System.currentTimeMillis(), DateFormat.MEDIUM, DateFormat.MEDIUM));
                // Change format from HH:dd:ss to "X Days Y Hours Z Minutes"
                long secondsInMilli = 1000;
                long minutesInMilli = secondsInMilli * 60;
                long hoursInMilli = minutesInMilli * 60;
                long daysInMilli = hoursInMilli * 24;

                long totalTime = pkgStats.totalTime;

                long elapsedDays = totalTime / daysInMilli;
                totalTime %= daysInMilli;

                long elapsedHours = totalTime / hoursInMilli;
                totalTime %= hoursInMilli;

                long elapsedMinutes = totalTime / minutesInMilli;

                String time;
                if (elapsedDays == 0) {
                    if (elapsedHours == 0) {
                        time = elapsedMinutes + getString(R.string.minutes_tag);
                    } else {
                        time = elapsedHours + getString(R.string.hours_tag) + elapsedMinutes + getString(R.string.minutes_tag);
                    }
                } else {
                    time = elapsedDays + getString(R.string.days_tag) + elapsedHours + getString(R.string.hours_tag) + elapsedMinutes + getString(R.string.minutes_tag);
                }
                holder.usageTime.setText(time);

//                holder.usageTime.setText(
//                        DateUtils.formatElapsedTime(pkgStats.getTotalTimeInForeground() / 1000));
                double usageTimeInt = pkgStats.totalTime / (double) 3600000;

                if (usageTimeInt <= xDays * Constants.CORRECT_USAGE_APP)
                    holder.usageTime.setTextColor(getColor(R.color.colorPrimary));
                else if (usageTimeInt > xDays * Constants.DANGEROUS_USAGE_APP)
                    holder.usageTime.setTextColor(Color.RED);
                else holder.usageTime.setTextColor(Color.rgb(255, 128, 64));
            } else {
                Log.w(TAG, "No usage stats info for package:" + position);
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() { return mPackageStats.size(); }

        public void sortList(int sortOrder) {
            if (mDisplayOrder == sortOrder) {
                notifyDataSetChanged();
                return;
            }
            mDisplayOrder = sortOrder;
            sortList();
        }

        private void sortList() {
            if (mDisplayOrder == _DISPLAY_ORDER_USAGE_TIME) {
                Log.i(TAG, "Sorting by usage time");
                mPackageStats.sort(mUsageTimeComparator);
            } else if (mDisplayOrder == _DISPLAY_ORDER_LAST_TIME_USED) {
                Log.i(TAG, "Sorting by last time used");
                mPackageStats.sort(mLastTimeUsedComparator);
            } else if (mDisplayOrder == _DISPLAY_ORDER_APP_NAME) {
                Log.i(TAG, "Sorting by application name");
                mPackageStats.sort(mAppLabelComparator);
            }
            notifyDataSetChanged();
        }

        public class MyViewHolder extends RecyclerView.ViewHolder {
            TextView pkgName, lastTimeUsed, usageTime;
            ImageView icon;

            protected View mRootView;

            MyViewHolder(@NonNull View itemView) {
                super(itemView);

                mRootView = itemView;

                pkgName = mRootView.findViewById(R.id.package_name);
                lastTimeUsed = mRootView.findViewById(R.id.last_time_used);
                usageTime = mRootView.findViewById(R.id.usage_time);
                icon = mRootView.findViewById(R.id.usage_icon);
            }
        }
    }

}
