package com.etiennelawlor.loop.adapters;

import android.graphics.Typeface;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.etiennelawlor.loop.LoopApplication;
import com.etiennelawlor.loop.R;
import com.etiennelawlor.loop.network.models.response.Interaction;
import com.etiennelawlor.loop.network.models.response.Interactions;
import com.etiennelawlor.loop.network.models.response.Metadata;
import com.etiennelawlor.loop.network.models.response.Pictures;
import com.etiennelawlor.loop.network.models.response.Size;
import com.etiennelawlor.loop.network.models.response.Stats;
import com.etiennelawlor.loop.network.models.response.Tag;
import com.etiennelawlor.loop.network.models.response.User;
import com.etiennelawlor.loop.network.models.response.Video;
import com.etiennelawlor.loop.otto.BusProvider;
import com.etiennelawlor.loop.otto.events.SearchPerformedEvent;
import com.etiennelawlor.loop.ui.AvatarView;
import com.etiennelawlor.loop.ui.LoadingImageView;
import com.etiennelawlor.loop.utilities.DateUtility;
import com.etiennelawlor.loop.utilities.FontCache;
import com.etiennelawlor.loop.utilities.Transformers;
import com.greenfrvr.hashtagview.HashtagView;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import butterknife.Bind;
import butterknife.ButterKnife;
import timber.log.Timber;

/**
 * Created by etiennelawlor on 5/23/15.
 */

public class RelatedVideosAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // region Constants
    public static final int HEADER = 0;
    public static final int ITEM = 1;
    public static final int FOOTER = 2;
    // endregion

    // region Member Variables
    private Video video;
    private List<Video> videos;
    private OnItemClickListener onItemClickListener;
    private OnLikeClickListener onLikeClickListener;
    private OnWatchLaterClickListener onWatchLaterClickListener;
    private OnCommentsClickListener onCommentsClickListener;
    private OnInfoClickListener onInfoClickListener;
    private OnReloadClickListener onReloadClickListener;
    private FooterViewHolder footerViewHolder;
    private boolean isFooterAdded = false;
    private Typeface boldFont;
    private boolean isLikeOn = false;
    private boolean isWatchLaterOn = false;
    private boolean hasDescription = false;
    private boolean hasTags = false;
    // endregion

    // region Listeners
    // endregion

    // region Interfaces
    public interface OnItemClickListener {
        void onItemClick(int position, View view);
    }

    public interface OnReloadClickListener {
        void onReloadClick();
    }

    public interface OnLikeClickListener {
        void onLikeClick(ImageView imageView);
    }

    public interface OnWatchLaterClickListener {
        void onWatchLaterClick(ImageView imageView);
    }

    public interface OnCommentsClickListener {
        void onCommentsClick();
    }

    public interface OnInfoClickListener {
        void onInfoClick(ImageView imageView);
    }
    // endregion

    // region Constructors
    public RelatedVideosAdapter(Video video) {
        this.video = video;
        videos = new ArrayList<>();

        boldFont = FontCache.getTypeface("Ubuntu-Bold.ttf", LoopApplication.getInstance().getApplicationContext());
    }
    // endregion

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder viewHolder = null;

        switch (viewType) {
            case HEADER:
                viewHolder = createHeaderViewHolder(parent);
                break;
            case ITEM:
                viewHolder = createVideoViewHolder(parent);
                break;
            case FOOTER:
                viewHolder = createFooterViewHolder(parent);
                break;
            default:
                Timber.e("[ERR] type is not supported!!! type is %d", viewType);
                break;
        }

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        switch (getItemViewType(position)) {
            case HEADER:
                bindHeaderViewHolder(viewHolder);
                break;
            case ITEM:
                bindVideoViewHolder(viewHolder, position);
                break;
            case FOOTER:
                bindFooterViewHolder(viewHolder);
                break;
            default:
                break;
        }
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    @Override
    public int getItemViewType(int position) {
        if(position == 0)
            return HEADER;
        else
            return (position == getItemCount()-1 && isFooterAdded) ? FOOTER : ITEM;
    }

    // region Helper Methods
    private RecyclerView.ViewHolder createHeaderViewHolder(ViewGroup parent){
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.video_info, parent, false);
        final HeaderViewHolder holder = new HeaderViewHolder(v);

        holder.likeImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(onLikeClickListener != null){
                    onLikeClickListener.onLikeClick(holder.likeImageView);
                }
            }
        });

        holder.watchLaterImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(onWatchLaterClickListener != null){
                    onWatchLaterClickListener.onWatchLaterClick(holder.watchLaterImageView);
                }
            }
        });

        holder.commentsImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(onCommentsClickListener != null){
                    onCommentsClickListener.onCommentsClick();
                }
            }
        });

        holder.infoImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(onInfoClickListener != null){
                    onInfoClickListener.onInfoClick(holder.infoImageView);
                    int visibility = holder.additionalInfoLinearLayout.getVisibility();
                    if(visibility == View.VISIBLE){
                        holder.additionalInfoLinearLayout.setVisibility(View.GONE);
                    } else if(visibility == View.GONE){
                        holder.additionalInfoLinearLayout.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        return holder;
    }

    private RecyclerView.ViewHolder createVideoViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.video_row, parent, false);
        final VideoViewHolder holder = new VideoViewHolder(v);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int adapterPos = holder.getAdapterPosition();
                if(adapterPos != RecyclerView.NO_POSITION){
                    if (onItemClickListener != null) {
                        onItemClickListener.onItemClick(adapterPos, holder.itemView);
                    }
                }
            }
        });

        return holder;
    }

    private RecyclerView.ViewHolder createFooterViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_footer, parent, false);

        final FooterViewHolder holder = new FooterViewHolder(v);
        holder.reloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(onReloadClickListener != null){
                    onReloadClickListener.onReloadClick();
                }
            }
        });

        return holder;
    }

    private void bindHeaderViewHolder(RecyclerView.ViewHolder viewHolder) {
        HeaderViewHolder holder = (HeaderViewHolder) viewHolder;

        if(video != null){
            setUpTitle(holder.titleTextView, video);
            setUpSubtitle(holder.subtitleTextView, video);
            setUpViewCount(holder.viewCountTextView, video);
            setUpLike(holder.likeImageView, video);
            setUpWatchLater(holder.watchLaterImageView, video);
            setUpUserImage(holder.userImageView, video);
            setUpUploadedDate2(holder.uploadDateTextView, video);
            setUpDescription(holder.descriptionTextView, video);
            setUpTags(holder.hashtagView, video);
            setUpInfoImage(holder.infoImageView);
        }
    }

    private void bindVideoViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        final VideoViewHolder holder = (VideoViewHolder) viewHolder;

        final Video video = videos.get(position);
        if (video != null) {
            setUpTitle(holder.titleTextView, video);
            setUpSubtitle(holder.subtitleTextView, video);
            setUpVideoThumbnail(holder.videoThumbnailImageView, video);
            setUpDuration(holder.durationTextView, video);
            setUpUploadedDate(holder.uploadedDateTextView, video);
        }
    }

    private void bindFooterViewHolder(RecyclerView.ViewHolder viewHolder) {
        FooterViewHolder holder = (FooterViewHolder) viewHolder;
        footerViewHolder = holder;

        holder.loadingImageView.setMaskOrientation(LoadingImageView.MaskOrientation.LeftToRight);
    }

    private void add(Video item) {
        videos.add(item);
        notifyItemInserted(getItemCount()-1);
    }

    public void addAll(List<Video> videos) {
        for (Video video : videos) {
            add(video);
        }
    }

    public void remove(Video item) {
        int position = videos.indexOf(item);
        if (position > -1) {
            videos.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void clear() {
        isFooterAdded = false;
        while (getItemCount() > 0) {
            remove(getItem(0));
        }
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    public void addHeader(){
        add(new Video());
    }

    public void addFooter(){
        isFooterAdded = true;
        add(new Video());
    }

    public void removeFooter() {
        isFooterAdded = false;

        int position = videos.size() - 1;
        Video item = getItem(position);

        if (item != null) {
            videos.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void updateFooter(FooterType footerType){
        switch (footerType) {
            case LOAD_MORE:
                if(footerViewHolder!= null){
                    footerViewHolder.errorRelativeLayout.setVisibility(View.GONE);
                    footerViewHolder.loadingRelativeLayout.setVisibility(View.VISIBLE);
                }
                break;
            case ERROR:
                if(footerViewHolder!= null){
                    footerViewHolder.loadingRelativeLayout.setVisibility(View.GONE);
                    footerViewHolder.errorRelativeLayout.setVisibility(View.VISIBLE);
                }
                break;
            default:
                break;
        }
    }

    public Video getItem(int position) {
        return videos.get(position);
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    public void setOnReloadClickListener(OnReloadClickListener onReloadClickListener) {
        this.onReloadClickListener = onReloadClickListener;
    }

    public void setOnLikeClickListener(OnLikeClickListener onLikeClickListener) {
        this.onLikeClickListener = onLikeClickListener;
    }

    public void setOnWatchLaterClickListener(OnWatchLaterClickListener onWatchLaterClickListener) {
        this.onWatchLaterClickListener = onWatchLaterClickListener;
    }

    public void setOnCommentsClickListener(OnCommentsClickListener onCommentsClickListener) {
        this.onCommentsClickListener = onCommentsClickListener;
    }

    public void setOnInfoClickListener(OnInfoClickListener onInfoClickListener) {
        this.onInfoClickListener = onInfoClickListener;
    }

    private void setUpTitle(TextView tv, Video video) {
        String name = video.getName();
        if (!TextUtils.isEmpty(name)) {
            tv.setText(name);
        }
    }

    private void setUpSubtitle(TextView tv, Video video) {
        User user = video.getUser();
        if (user != null) {
            String userName = user.getName();
            if (!TextUtils.isEmpty(userName)) {
                tv.setText(userName);
            }
        }
    }

    private void setUpLike(ImageView iv, Video video){
        Metadata metadata = video.getMetadata();
        if (metadata != null) {
            Interactions interactions = metadata.getInteractions();
            if (interactions != null) {
                Interaction likeInteraction = interactions.getLike();

                if (likeInteraction != null) {
                    if (likeInteraction.getAdded()) {
                        setIsLikeOn(true);
                        iv.setImageResource(R.drawable.ic_like_on);
                    }
                }
            }
        }
    }

    private void setUpWatchLater(ImageView iv, Video video){
        Metadata metadata = video.getMetadata();
        if (metadata != null) {
            Interactions interactions = metadata.getInteractions();
            if (interactions != null) {
                Interaction watchLaterInteraction = interactions.getWatchlater();

                if (watchLaterInteraction != null) {
                    if (watchLaterInteraction.getAdded()) {
                        setIsWatchLaterOn(true);
                        iv.setImageResource(R.drawable.ic_watch_later_on);
                    }
                }
            }
        }
    }

    public boolean isLikeOn() {return isLikeOn; }

    public void setIsLikeOn(boolean isLikeOn) { this.isLikeOn = isLikeOn; }

    public boolean isWatchLaterOn() {return isWatchLaterOn; }

    public void setIsWatchLaterOn(boolean isWatchLaterOn) { this.isWatchLaterOn = isWatchLaterOn; }

    private void setUpVideoThumbnail(ImageView iv, Video video) {
        Pictures pictures = video.getPictures();
        if (pictures != null) {
            List<Size> sizes = pictures.getSizes();
            if (sizes != null && sizes.size() > 0) {
                Size size = sizes.get(sizes.size() - 1);
                if (size != null) {
                    String link = size.getLink();
                    if (!TextUtils.isEmpty(link)) {
                        Glide.with(iv.getContext())
                                .load(link)
//                                .placeholder(R.drawable.ic_placeholder)
//                                .error(R.drawable.ic_error)
                                .into(iv);
                    }
                }
            }
        }
    }

    private void setUpDuration(TextView tv, Video video) {
        Integer duration = video.getDuration();

        long minutes = duration / 60;
        long seconds = duration % 60;

        String time;
        if (minutes == 0L) {
            if (seconds > 0L) {
                if (seconds < 10L)
                    time = String.format("0:0%s", String.valueOf(seconds));
                else
                    time = String.format("0:%s", String.valueOf(seconds));
            } else {
                time = "0:00";
            }

        } else {
            if (seconds > 0L) {
                if (seconds < 10L)
                    time = String.format("%s:0%s", String.valueOf(minutes), String.valueOf(seconds));
                else
                    time = String.format("%s:%s", String.valueOf(minutes), String.valueOf(seconds));
            } else {
                time = String.format("%s:00", String.valueOf(minutes));
            }
        }

        tv.setText(time);
    }

    private void setUpUploadedDate(TextView tv, Video video) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ", Locale.ENGLISH);
        String uploadDate = "";

        String createdTime = video.getCreatedTime();
        try {
            Date date = sdf.parse(createdTime);

            Calendar futureCalendar = Calendar.getInstance();
            futureCalendar.setTime(date);

            uploadDate = DateUtility.getRelativeDate(futureCalendar);
        } catch (ParseException e) {
            Timber.e("");
        }

        int viewCount = 0;
        Stats stats = video.getStats();
        if (stats != null) {
            viewCount = stats.getPlays();
        }

        if (viewCount > 0) {
//                String formattedViewCount = NumberFormat.getNumberInstance(Locale.US).format(viewCount);
            String formattedViewCount = formatViewCount(viewCount);
            if(!TextUtils.isEmpty(uploadDate))
                tv.setText(String.format("%s \u2022 %s", formattedViewCount, uploadDate));
            else
                tv.setText(formattedViewCount);

        } else {
            tv.setText(String.format("%s", uploadDate));
        }
    }

    private String formatViewCount(int viewCount) {
        String formattedViewCount = "";

        if (viewCount < 1000000000 && viewCount >= 1000000) {
            formattedViewCount = String.format("%dM views", viewCount / 1000000);
        } else if (viewCount < 1000000 && viewCount >= 1000) {
            formattedViewCount = String.format("%dK views", viewCount / 1000);
        } else if (viewCount < 1000 && viewCount > 1) {
            formattedViewCount = String.format("%d views", viewCount);
        } else if (viewCount == 1) {
            formattedViewCount = String.format("%d view", viewCount);
        }

        return formattedViewCount;
    }

    private void setUpUserImage(AvatarView av, Video video) {
        User user = video.getUser();
        if(user != null){
            av.bind(user);
        } else {
            av.nullify();
        }
    }

    private void setUpViewCount(TextView tv, Video video) {
        int viewCount = 0;
        Stats stats = video.getStats();
        if (stats != null) {
            viewCount = stats.getPlays();
        }

        if (viewCount > 0) {
            String formattedViewCount = NumberFormat.getNumberInstance(Locale.US).format(viewCount);
//                String formattedViewCount = formatViewCount(viewCount);
            if (viewCount > 1) {
                tv.setText(String.format("%s views", formattedViewCount));
            } else {
                tv.setText(String.format("%s view", formattedViewCount));
            }
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private void setUpUploadedDate2(TextView tv, Video video) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ", Locale.ENGLISH);
        String uploadDate = "";

        String createdTime = video.getCreatedTime();

        try {
            Date date = sdf.parse(createdTime);

            Calendar futureCalendar = Calendar.getInstance();
            futureCalendar.setTime(date);

            uploadDate = DateUtility.getRelativeDate(futureCalendar);
        } catch (ParseException e) {
            Timber.e("");
        }

        if (!TextUtils.isEmpty(uploadDate)) {
            tv.setText(String.format("Uploaded %s", uploadDate));
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private void setUpTags(final HashtagView htv, Video video) {
        List<Tag> tags = video.getTags();
        if (tags != null && tags.size() > 0) {
            ArrayList<String> canonicalTags = new ArrayList<>();
            Timber.d("setUpTags() : tags.size() - " + tags.size());

            for (Tag tag : tags) {
                String canonicalTag = tag.getCanonical();
                if(canonicalTag.length() > 0) {
                    Timber.d("setUpTags() : canonicalTag - " + canonicalTag);
                    canonicalTags.add(canonicalTag);
                }
            }

            Timber.d("setUpTags() : canonicalTags.size() - " + canonicalTags.size());

            if(canonicalTags.size() > 0){
                hasTags = true;
                htv.setData(canonicalTags, Transformers.HASH);
                htv.setTypeface(boldFont);
                htv.addOnTagClickListener(new HashtagView.TagsClickListener() {
                    @Override
                    public void onItemClicked(Object item) {
                        String tag = (String) item;
                        Timber.d("setUpTags() : tag - " + tag);

                        BusProvider.getInstance().post(new SearchPerformedEvent(tag));
                    }
                });
                htv.setVisibility(View.VISIBLE);
            } else {
                htv.setVisibility(View.GONE);
            }
        }
    }


    private void setUpDescription(TextView tv, Video video) {
        String description = video.getDescription();
        if (!TextUtils.isEmpty(description)) {
            hasDescription = true;
//            description = description.replaceAll("[\\t\\n\\r]+", "\n");
            tv.setText(description.trim());
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private void setUpInfoImage(ImageView iv){
        if(hasDescription || hasTags){
            iv.setVisibility(View.VISIBLE);
        }
    }

    // endregion

    // region Inner Classes

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        // region Views
        @Bind(R.id.title_tv)
        TextView titleTextView;
        @Bind(R.id.subtitle_tv)
        TextView subtitleTextView;
        @Bind(R.id.user_iv)
        AvatarView userImageView;
        @Bind(R.id.view_count_tv)
        TextView viewCountTextView;
        @Bind(R.id.upload_date_tv)
        TextView uploadDateTextView;
        @Bind(R.id.like_iv)
        ImageView likeImageView;
        @Bind(R.id.watch_later_iv)
        ImageView watchLaterImageView;
        @Bind(R.id.comments_iv)
        ImageView commentsImageView;
        @Bind(R.id.info_iv)
        ImageView infoImageView;
        @Bind(R.id.htv)
        HashtagView hashtagView;
        @Bind(R.id.description_tv)
        TextView descriptionTextView;
        @Bind(R.id.additional_info_ll)
        LinearLayout additionalInfoLinearLayout;
        // endregion

        // region Constructors
        HeaderViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
        // endregion
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        // region Views
        @Bind(R.id.video_thumbnail_iv)
        ImageView videoThumbnailImageView;
        @Bind(R.id.title_tv)
        TextView titleTextView;
        @Bind(R.id.uploaded_date_tv)
        TextView uploadedDateTextView;
        @Bind(R.id.duration_tv)
        TextView durationTextView;
        @Bind(R.id.subtitle_tv)
        TextView subtitleTextView;
        // endregion

        // region Constructors
        VideoViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
        // endregion
    }

    public static class FooterViewHolder extends RecyclerView.ViewHolder {
        // region Views
        @Bind(R.id.loading_rl)
        RelativeLayout loadingRelativeLayout;
        @Bind(R.id.error_rl)
        RelativeLayout errorRelativeLayout;
        @Bind(R.id.loading_iv)
        LoadingImageView loadingImageView;
        @Bind(R.id.reload_btn)
        Button reloadButton;
        // endregion

        // region Constructors
        public FooterViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
        // endregion
    }

    public enum FooterType {
        LOAD_MORE,
        ERROR
    }

    // endregion

}