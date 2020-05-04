package com.example.adictic.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.adictic.R;
import com.example.adictic.TodoApp;
import com.example.adictic.entity.AppUsage;
import com.example.adictic.entity.GeneralUsage;
import com.example.adictic.entity.YearEntity;
import com.example.adictic.rest.TodoApi;
import com.example.adictic.util.Funcions;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.whiteelephant.monthpicker.MonthPickerDialog;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Informe extends AppCompatActivity {

     TodoApi mTodoService;
     PieChart pieChart;
     TextView TV_pieApp;
     BarChart barChart;
     Long idChild;
     Button dateButton;
     Map<Integer,Map<Integer,List<Integer>>> daysMap;
     List<Integer> yearList;
     List<Integer> monthList;
     int currentYear;
     int currentMonth;
     TextView error;
     TextView TV_percentageUsage;
     TextView TV_totalUsage;
     double percentage;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.informe_layout);
        mTodoService = ((TodoApp) getApplication()).getAPI();

        pieChart = (PieChart) findViewById(R.id.Ch_Pie);
        barChart = (BarChart) findViewById(R.id.Ch_Line);
        TV_pieApp = (TextView) findViewById(R.id.TV_PieChart);
        dateButton = (Button) findViewById(R.id.BT_monthPicker);
        error = (TextView) findViewById(R.id.TV_errorNoData);
        TV_percentageUsage = (TextView) findViewById(R.id.TV_percentageUsage);
        TV_totalUsage = (TextView) findViewById(R.id.TV_totalUse);

        daysMap = new HashMap<>();
        monthList = new ArrayList<>();
        yearList = new ArrayList<>();

        idChild = getIntent().getLongExtra("idChild",-1);

        getMonthYearLists();

        TV_percentageUsage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String deviceTime = TV_totalUsage.getText().toString().substring(0,TV_totalUsage.getText().toString().indexOf('/')-2);
                String totalTime = TV_totalUsage.getText().toString().substring(TV_totalUsage.getText().toString().indexOf('/')+2,TV_totalUsage.getText().length()-1);
                DecimalFormat decimalFormat = new DecimalFormat("###.##");

                AlertDialog dialog = new AlertDialog.Builder(Informe.this).create();
                dialog.setMessage(getResources().getString(R.string.percentage_info,deviceTime,totalTime,decimalFormat.format(percentage)));
                dialog.setButton(AlertDialog.BUTTON_POSITIVE,getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                dialog.show();
            }
        });
    }

    private void showError(){
        error.setVisibility(View.VISIBLE);
        dateButton.setVisibility(View.GONE);

        TextView bye = (TextView) findViewById(R.id.TV_mes);
        bye.setVisibility(View.GONE);
    }

    public void getMonthYearLists(){
        Call<List<YearEntity>> call = mTodoService.getDaysWithData(idChild);

        call.enqueue(new Callback<List<YearEntity>>() {
            @Override
            public void onResponse(Call<List<YearEntity>> call, Response<List<YearEntity>> response) {
                if(response.isSuccessful()){
                    /** Agafem les dades de response i convertim en map **/
                    List<YearEntity> yEntityList = response.body();
                    daysMap = Funcions.convertYearEntityToMap(yEntityList);
                    System.out.println(daysMap.keySet());
                    yearList.addAll(daysMap.keySet());
                    Collections.sort(yearList,Collections.reverseOrder());

                    currentYear = yearList.get(0);

                    monthList.addAll(daysMap.get(yearList.get(0)).keySet());
                    Collections.sort(monthList,Collections.reverseOrder());

                    currentMonth = monthList.get(0)-1;
                    dateButton.setText(getResources().getStringArray(R.array.month_names)[currentMonth+1]+" "+currentYear);

                    getStats(currentMonth+1,currentYear);
                }
                else{
                    showError();
                }
            }

            @Override
            public void onFailure(Call<List<YearEntity>> call, Throwable t) {
                showError();
            }
        });
    }

    public void btnMonthYear(View view){
        //Si no va, treu final
        final MonthPickerDialog.Builder builder = new MonthPickerDialog.Builder(this,
                new MonthPickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(int selectedMonth, int selectedYear) {
                        currentMonth = selectedMonth;
                        currentYear = selectedYear;
                        getStats(currentMonth+1,currentYear);
                        dateButton.setText(getResources().getStringArray(R.array.month_names)[currentMonth+1]+" "+currentYear);
                    }}, currentYear, currentMonth);

        monthList.addAll(daysMap.get(yearList.get(0)).keySet());
        Collections.sort(monthList,Collections.reverseOrder());

        if(yearList.size()==1) builder.showMonthOnly();

        builder .setActivatedMonth(currentMonth)
                .setActivatedYear(currentYear)
                .setTitle("Tria el mes per veure l'informe")
                .setMonthAndYearRange(monthList.get(monthList.size()-1)-1, monthList.get(0)-1, yearList.get(yearList.size()-1), yearList.get(0))
                .setOnYearChangedListener(new MonthPickerDialog.OnYearChangedListener() {
                                 @Override
                                 public void onYearChanged(int selectedYear) { // on year selected
                                     monthList.addAll(daysMap.get(yearList.get(selectedYear)).keySet());
                                     Collections.sort(monthList,Collections.reverseOrder());
                                     builder.setMonthRange(monthList.get(monthList.size()-2), monthList.get(0)-1);

                                 }
                })
                .build()
                .show();
    }

    private void getStats(int month, int year){
        Call<Collection<GeneralUsage>> call = mTodoService.getGenericAppUsage(idChild,month+"-"+year,month+"-"+year);
        call.enqueue(new Callback<Collection<GeneralUsage>>() {
            @Override
            public void onResponse(Call<Collection<GeneralUsage>> call, Response<Collection<GeneralUsage>> response) {
                if (response.isSuccessful()) {
                    makeGraphs(response.body());
                } else {
                    showError();
                }
            }

            @Override
            public void onFailure(Call<Collection<GeneralUsage>> call, Throwable t) {
                showError();
            }
        });
    }

    private void makeGraphs(Collection<GeneralUsage> col){
        Map<String,Long> mapUsage = new HashMap<>();

        List<GeneralUsage> llista = getUsageMonths(currentMonth+1,currentYear,col);

        List<BarEntry> barEntries = new ArrayList<>();

        long totalUsageTime = 0;
        long totalTime = 0;

        for(GeneralUsage gu : llista){
            totalTime+=24*60*60*1000;
            totalUsageTime+=gu.totalTime;
            for(AppUsage au: gu.usage){
                if(mapUsage.containsKey(au.appName)) mapUsage.put(au.appName,mapUsage.get(au.appName)+au.totalTime);
                else mapUsage.put(au.appName,au.totalTime);
            }
            barEntries.add(new BarEntry(gu.day+(gu.month*100),gu.totalTime/3600000));
        }

        percentage = totalUsageTime*100.0f/totalTime;
        TV_percentageUsage.setText(getResources().getString(R.string.percentage,Math.round(percentage)));

        Pair<Integer,Integer> usagePair = Funcions.millisToString(totalUsageTime);
        Pair<Integer,Integer> totalTimePair = Funcions.millisToString(totalTime);

        if(usagePair.first == 0){
            String first = getResources().getString(R.string.mins,usagePair.second);
            String second = getResources().getString(R.string.hrs,totalTimePair.first);
            TV_totalUsage.setText(getResources().getString(R.string.comp,first,second));
        }
        else{
            String first = getResources().getString(R.string.hours_minutes,usagePair.first,usagePair.second);
            String second = getResources().getString(R.string.hrs,totalTimePair.first);
            TV_totalUsage.setText(getResources().getString(R.string.comp,first,second));
        }

        setBarChart(barEntries);
        setPieChart(mapUsage,totalUsageTime);
    }

    private void setPieChart(Map<String,Long> mapUsage, long totalUsageTime){
        ArrayList<PieEntry> yValues = new ArrayList<>();
        long others = 0;
        for(Map.Entry<String,Long> entry : mapUsage.entrySet()){
            if(entry.getValue() >= totalUsageTime*0.05) yValues.add(new PieEntry(entry.getValue(),entry.getKey()));
            else{
                others+=entry.getValue();
            }
        }

        yValues.add(new PieEntry(others,"Altres"));

        PieDataSet pieDataSet = new PieDataSet(yValues, "Ús d'apps");
        pieDataSet.setSliceSpace(3f);
        pieDataSet.setSelectionShift(5f);
        pieDataSet.setColors(ColorTemplate.MATERIAL_COLORS);

        PieData pieData = new PieData(pieDataSet);
        pieData.setValueFormatter(new PercentFormatter());
        pieData.setValueTextSize(10);

        pieChart.setData(pieData);
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setCenterTextSize(25);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setTransparentCircleRadius(61f);

        pieChart.animateY(1000);

        pieChart.setDrawEntryLabels(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                PieEntry pe = (PieEntry) e;

                TV_pieApp.setText(pe.getLabel());

                Pair<Integer,Integer> appTime = Funcions.millisToString(e.getY());

                if(appTime.first == 0) pieChart.setCenterText(getResources().getString(R.string.mins,appTime.second));
                else pieChart.setCenterText(getResources().getString(R.string.hours_endl_minutes,appTime.first,appTime.second));

            }

            @Override
            public void onNothingSelected() {
                TV_pieApp.setText(getResources().getString(R.string.press_pie_chart));
                pieChart.setCenterText("");
            }
        });
        pieChart.invalidate();
    }

    private void setBarChart(List<BarEntry> entries){
        BarDataSet barDataSet = new BarDataSet(entries,getResources().getString(R.string.daily_usage));
        barDataSet.setColors(ColorTemplate.MATERIAL_COLORS);

        BarData barData = new BarData(barDataSet);

        barChart.setData(barData);

        barChart.animateY(1000);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);
        xAxis.setValueFormatter(new MyXAxisBarFormatter());
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawLabels(true);

        //barChart.setExtraBottomOffset(30);

        barChart.getAxisLeft().setValueFormatter(new MyYAxisBarFormatter());
        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.getDescription().setEnabled(false);

        barChart.invalidate();
    }

    static class MyXAxisBarFormatter extends ValueFormatter {
        List<String> mesos = Arrays.asList(" Gen"," Feb"," Mar"," Abr"," Maig"," Jun"," Jul"," Ago"," Set"," Oct"," Nov"," Des");

        @Override
        public String getFormattedValue(float value){
            int mes = (int) value/100;
            int dia = (int) value%100;
            return dia+mesos.get(mes-1);
        }
    }

    static class MyYAxisBarFormatter extends ValueFormatter {
        @Override
        public String getFormattedValue(float value){
            return Math.round(value)+"h.";
        }
    }

    private List<GeneralUsage> getUsageMonths(Integer month, Integer year, Collection<GeneralUsage> col){
        List<GeneralUsage> resultat = new ArrayList<>();

        for(GeneralUsage gu : col){
            if(gu.month.equals(month) && gu.year.equals(year)) resultat.add(gu);
        }

        return resultat;
    }

}