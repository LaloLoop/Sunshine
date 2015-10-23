package com.example.android.sunshine.app;

import android.content.Context;
import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

/**
 * {@link ForecastAdapter} exposes a list of weather forecasts
 * from a {@link android.database.Cursor} to a {@link android.widget.ListView}.
 */
public class ForecastAdapter extends RecyclerView.Adapter<ForecastAdapter.ViewHolder> {

    private final int VIEW_TYPE_TODAY = 0;
    private final int VIEW_TYPE_FUTURE_DAY = 1;
    private final int VIEW_TYPE_COUNT = 2;

    private boolean mUseSpecialTodayView;

    private Context mContext;
    private Cursor mCursor;

    private ViewHolder.ForecastViewHolderClick mForecastClickListener;
    private View mEmptyView;

    public ForecastAdapter(
            Cursor cursor,
            Context context,
            ViewHolder.ForecastViewHolderClick listener,
            View emptyView) {
        mCursor = cursor;
        mContext = context;
        mForecastClickListener = listener;
        mEmptyView = emptyView;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Choose the layout type.
        int layoutId;

        switch (viewType){
            case VIEW_TYPE_TODAY:
                layoutId = R.layout.list_item_forecast_today;
                break;
            case VIEW_TYPE_FUTURE_DAY:
                layoutId = R.layout.list_item_forecast;
                break;
            default:
                throw new UnsupportedOperationException("Unknown list item layout");
        }

        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);

        ViewHolder vh = new ViewHolder(view, mForecastClickListener);
        view.setTag(vh);

        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        mCursor.moveToPosition(position);

        // Read weather icon ID from cursor
        int weatherId = mCursor.getInt(ForecastFragment.COL_WEATHER_CONDITION_ID);

        // Get art or ic depending on view type
        int defaultImage;
        if( getItemViewType(mCursor.getPosition()) == VIEW_TYPE_FUTURE_DAY ) {
            defaultImage = Utility.getIconResourceForWeatherCondition(weatherId);
        } else {
            defaultImage = Utility.getArtResourceForWeatherCondition(weatherId);
        }

        if(Utility.usingLocalGraphics(mContext)) {
            holder.mIconView.setImageResource(defaultImage);
        } else {
            // Use a placeholder image for now
            Glide.with(mContext).load(Utility.getArtUrlForWeatherCondition(mContext, weatherId))
                    .error(defaultImage)
                    .crossFade()
                    .into(holder.mIconView);
        }


        // Read date from cursor
        long date = mCursor.getLong(ForecastFragment.COL_WEATHER_DATE);
        holder.mDateView.setText(Utility.getFriendlyDayString(mContext, date));

        // Read weather forecast from cursor
        String description = Utility.getStringForWeatherCondition(mContext, weatherId);
        holder.mForecastView.setText(description);
        holder.mForecastView.setContentDescription(mContext.getString(R.string.a11y_forecast, description));

        // Read user preference for metric
        boolean isMetric = Utility.isMetric(mContext);

        // Read high temperature from cursor
        double high = mCursor.getDouble(ForecastFragment.COL_WEATHER_MAX_TEMP);
        String highStr = Utility.formatTemperature(mContext, high, isMetric);
        holder.mHighView.setText(highStr);
        holder.mHighView.setContentDescription(mContext.getString(R.string.a11y_high_temp, highStr));

        // Read low temperature from cursor
        double low = mCursor.getDouble(ForecastFragment.COL_WEATHER_MIN_TEMP);
        String lowStr = Utility.formatTemperature(mContext, low, isMetric);
        holder.mLowView.setText(lowStr);
        holder.mLowView.setContentDescription(mContext.getString(R.string.a11y_low_temp, lowStr));

        holder.bind(position);
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0 && mUseSpecialTodayView)? VIEW_TYPE_TODAY : VIEW_TYPE_FUTURE_DAY;
    }

    @Override
    public int getItemCount() {
        return mCursor != null ? mCursor.getCount() : 0;
    }

    public void setUseSpecialTodayView(boolean mUseSpecialTodayView) {
        this.mUseSpecialTodayView = mUseSpecialTodayView;
    }

    public Cursor getCursor() {
        return mCursor;
    }

    public void swapCursor(Cursor cursor) {
        this.mCursor = cursor;
        notifyDataSetChanged();
        if(cursor != null && getItemCount() > 0) {
            mEmptyView.setVisibility(View.INVISIBLE);
        } else {
            mEmptyView.setVisibility(View.VISIBLE);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        ImageView mIconView;
        TextView mForecastView;
        TextView mDateView;
        TextView mLowView;
        TextView mHighView;

        int mPosition;

        ForecastViewHolderClick mListener;

        public ViewHolder(View view, ForecastViewHolderClick listener) {
            super(view);
            mIconView = (ImageView) view.findViewById(R.id.list_item_icon);
            mForecastView = (TextView) view.findViewById(R.id.list_item_forecast_textview);
            mDateView = (TextView) view.findViewById(R.id.list_item_date_textview);
            mLowView = (TextView) view.findViewById(R.id.list_item_low_textview);
            mHighView = (TextView) view.findViewById(R.id.list_item_high_textview);
            this.mListener = listener;

            view.setOnClickListener(this);
        }

        public void bind(int position) {
            mPosition = position;
        }

        @Override
        public void onClick(View view) {
            if(mListener != null) {
                mListener.onItemClicked(mPosition);
            }
        }

        public interface ForecastViewHolderClick {
            void onItemClicked(int position);
        }
    }
}