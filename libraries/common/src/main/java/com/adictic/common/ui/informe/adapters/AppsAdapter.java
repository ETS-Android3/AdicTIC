package com.adictic.common.ui.informe.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.adictic.common.R;
import com.adictic.common.entity.CanvisAppBlock;
import com.adictic.common.util.Funcions;

import org.joda.time.DateTime;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.AppsViewHolder> {
    @SuppressLint("SimpleDateFormat")
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd MMMM yyyy");
    @SuppressLint("SimpleDateFormat")
    private final SimpleDateFormat timeFormatter = new SimpleDateFormat("hh:mm");

    private final LayoutInflater mInflater;
    private final ArrayList<CanvisAppBlock> appsList;
    private final Context mContext;

    public AppsAdapter(List<CanvisAppBlock> list, Context c){
        mContext = c;
        mInflater = LayoutInflater.from(c);
        appsList = new ArrayList<>(list);

        appsList.sort((o1, o2) -> o2.data.compareTo(o1.data));
    }

    @NonNull
    @Override
    public AppsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.informe_apps_item, parent);

        return new AppsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppsViewHolder holder, int position) {
        CanvisAppBlock canvisAppBlock = appsList.get(position);
        holder.TV_app_data.setText(dateFormatter.format(canvisAppBlock.data));
        String pkgName, appName;
        if(canvisAppBlock.appAntic == null){
            pkgName = canvisAppBlock.appNou.pkgName;
            appName = canvisAppBlock.appNou.appName;
        }
        else{
            pkgName = canvisAppBlock.appAntic.pkgName;
            appName = canvisAppBlock.appAntic.appName;
        }
        holder.TV_app_title.setText(appName);
        Funcions.setIconDrawable(mContext, pkgName, holder.IV_app_logo);

        if(canvisAppBlock.actiu)
            holder.TV_app_actiu.setVisibility(View.VISIBLE);
        else
            holder.TV_app_actiu.setVisibility(View.GONE);

        if(canvisAppBlock.appAntic == null){
            holder.TV_app_abans.setText(R.string.canvi_app_block);
            holder.TV_app_abans.setTextColor(Color.RED);
            if(canvisAppBlock.appNou.appTime == 0)
                holder.TV_app_despres.setText(R.string.canvis_app_block_permanent);
            else{
                DateTime dateTime = new DateTime().withMillisOfDay(Math.toIntExact(canvisAppBlock.appNou.appTime));
                holder.TV_app_despres.setText(timeFormatter.format(dateTime));
            }
        }
        else if(canvisAppBlock.appNou == null){
            holder.TV_app_despres.setText(R.string.canvis_app_unblock);
            holder.TV_app_despres.setTextColor(Color.GREEN);
            if(canvisAppBlock.appAntic.appTime == 0)
                holder.TV_app_abans.setText(R.string.canvis_app_block_permanent);
            else{
                DateTime dateTime = new DateTime().withMillisOfDay(Math.toIntExact(canvisAppBlock.appAntic.appTime));
                holder.TV_app_abans.setText(timeFormatter.format(dateTime));
            }
        }
        else{
            if(canvisAppBlock.appNou.appTime == 0)
                holder.TV_app_despres.setText(R.string.canvis_app_block_permanent);
            else{
                DateTime dateTime = new DateTime().withMillisOfDay(Math.toIntExact(canvisAppBlock.appNou.appTime));
                holder.TV_app_despres.setText(timeFormatter.format(dateTime));
            }

            if(canvisAppBlock.appAntic.appTime == 0)
                holder.TV_app_abans.setText(R.string.canvis_app_block_permanent);
            else{
                DateTime dateTime = new DateTime().withMillisOfDay(Math.toIntExact(canvisAppBlock.appAntic.appTime));
                holder.TV_app_abans.setText(timeFormatter.format(dateTime));
            }
        }
    }

    @Override
    public int getItemCount() {
        return appsList.size();
    }

    public static class AppsViewHolder extends RecyclerView.ViewHolder {
        View root;
        ImageView IV_app_logo;
        TextView TV_app_title, TV_app_data, TV_app_abans, TV_app_despres, TV_app_actiu;

        public AppsViewHolder(@NonNull View itemView) {
            super(itemView);

            root = itemView;

            IV_app_logo = root.findViewById(R.id.IV_app_logo);
            TV_app_data = root.findViewById(R.id.TV_app_data);
            TV_app_abans = root.findViewById(R.id.TV_app_abans);
            TV_app_despres = root.findViewById(R.id.TV_app_despres);
            TV_app_actiu = root.findViewById(R.id.TV_app_actiu);
            TV_app_title = root.findViewById(R.id.TV_app_title);
        }
    }
}
