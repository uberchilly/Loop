package com.etiennelawlor.loop.fragments;

import android.app.Activity;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.etiennelawlor.loop.EventMapKeys;
import com.etiennelawlor.loop.EventNames;
import com.etiennelawlor.loop.R;
import com.etiennelawlor.loop.activities.SearchableActivity;
import com.etiennelawlor.loop.activities.VideoCommentsActivity;
import com.etiennelawlor.loop.activities.VideoDetailsActivity;
import com.etiennelawlor.loop.activities.VideoPlayerActivity;
import com.etiennelawlor.loop.adapters.RelatedVideosAdapter;
import com.etiennelawlor.loop.analytics.Event;
import com.etiennelawlor.loop.analytics.EventLogger;
import com.etiennelawlor.loop.models.AccessToken;
import com.etiennelawlor.loop.network.ServiceGenerator;
import com.etiennelawlor.loop.network.VimeoService;
import com.etiennelawlor.loop.network.models.response.Pictures;
import com.etiennelawlor.loop.network.models.response.Size;
import com.etiennelawlor.loop.network.models.response.Video;
import com.etiennelawlor.loop.network.models.response.VideosCollection;
import com.etiennelawlor.loop.otto.BusProvider;
import com.etiennelawlor.loop.otto.events.SearchPerformedEvent;
import com.etiennelawlor.loop.otto.events.VideoLikedEvent;
import com.etiennelawlor.loop.otto.events.WatchLaterEvent;
import com.etiennelawlor.loop.prefs.LoopPrefs;
import com.etiennelawlor.loop.utilities.FontCache;
import com.etiennelawlor.loop.utilities.NetworkLogUtility;
import com.etiennelawlor.loop.utilities.TrestleUtility;
import com.squareup.otto.Subscribe;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Created by etiennelawlor on 5/23/15.
 */
public class VideoDetailsFragment extends BaseFragment implements RelatedVideosAdapter.OnItemClickListener,
        RelatedVideosAdapter.OnReloadClickListener,
        RelatedVideosAdapter.OnLikeClickListener,
        RelatedVideosAdapter.OnWatchLaterClickListener,
        RelatedVideosAdapter.OnCommentsClickListener,
        RelatedVideosAdapter.OnInfoClickListener {

    // region Constants
    public static final int PAGE_SIZE = 30;
    private static final int VIDEO_SHARE_REQUEST_CODE = 1002;
    public static final String KEY_VIDEO_ID = "KEY_VIDEO_ID";
    // endregion

    // region Views
    @Bind(R.id.video_thumbnail_iv)
    ImageView videoThumbnailImageView;
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.videos_rv)
    RecyclerView videosRecyclerView;
    // endregion

    // region Member Variables
    private Video video;
    private String transitionName;
    private RelatedVideosAdapter relatedVideosAdapter;
    private VimeoService vimeoService;
    private LinearLayoutManager layoutManager;
    private Long videoId = -1L;
    private boolean isLastPage = false;
    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean isInfoExpanded = false;
    private Typeface font;
    // endregion

    // region Listeners
    @OnClick(R.id.play_fab)
    public void onPlayFABClicked(final View v) {
        if (videoId != -1L) {
            Intent intent = new Intent(getActivity(), VideoPlayerActivity.class);

            Bundle bundle = new Bundle();
            bundle.putLong(KEY_VIDEO_ID, videoId);
            intent.putExtras(bundle);
            startActivity(intent);

            // Crashlytics Test Crash
            // throw new RuntimeException("This is a crash");
        }
    }

    private RecyclerView.OnScrollListener recyclerViewOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            int visibleItemCount = layoutManager.getChildCount();
            int totalItemCount = layoutManager.getItemCount();
            int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

            if (!isLoading && !isLastPage) {
                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                        && totalItemCount >= PAGE_SIZE) {
                    loadMoreItems();
                }
            }
        }
    };
    // endregion

    // region Callbacks

    private Callback<VideosCollection> getRelatedVideosFirstFetchCallback = new Callback<VideosCollection>() {
        @Override
        public void onResponse(Call<VideosCollection> call, Response<VideosCollection> response) {
            if (!response.isSuccessful()) {
                int responseCode = response.code();
                if(responseCode == 504) { // 504 Unsatisfiable Request (only-if-cached)
//                    errorTextView.setText("Can't load data.\nCheck your network connection.");
//                    errorLinearLayout.setVisibility(View.VISIBLE);
                }
                return;
            }

            VideosCollection videosCollection = response.body();
            if (videosCollection != null) {
                List<Video> videos = videosCollection.getVideos();
                if (videos != null) {
                    relatedVideosAdapter.addAll(videos);

                    if (videos.size() >= PAGE_SIZE) {
                        relatedVideosAdapter.addFooter();
                    } else {
                        isLastPage = true;
                    }
                }
            }
        }

        @Override
        public void onFailure(Call<VideosCollection> call, Throwable t) {
            NetworkLogUtility.logFailure(call, t);

            if(t instanceof ConnectException || t instanceof UnknownHostException){
//                errorTextView.setText("Can't load data.\nCheck your network connection.");
//                errorLinearLayout.setVisibility(View.VISIBLE);
            }
        }
    };

    private Callback<VideosCollection> getRelatedVideosNextFetchCallback = new Callback<VideosCollection>() {
        @Override
        public void onResponse(Call<VideosCollection> call, Response<VideosCollection> response) {
            relatedVideosAdapter.removeFooter();
            isLoading = false;

            if (!response.isSuccessful()) {
                int responseCode = response.code();
                switch (responseCode){
                    case 504: // 504 Unsatisfiable Request (only-if-cached)
                        break;
                    case 400:
                        isLastPage = true;
                        break;
                }
                return;
            }

            VideosCollection videosCollection = response.body();
            if (videosCollection != null) {
                List<Video> videos = videosCollection.getVideos();
                if (videos != null) {
                    Timber.d("onResponse() : Success : videos.size() - " + videos.size());
                    relatedVideosAdapter.addAll(videos);

                    if (videos.size() >= PAGE_SIZE) {
                        relatedVideosAdapter.addFooter();
                    } else {
                        isLastPage = true;
                    }
                }
            }
        }

        @Override
        public void onFailure(Call<VideosCollection> call, Throwable t) {
            NetworkLogUtility.logFailure(call, t);

            if(t instanceof ConnectException || t instanceof UnknownHostException){
                relatedVideosAdapter.updateFooter(RelatedVideosAdapter.FooterType.ERROR);
            }
        }
    };

    private Callback<ResponseBody> likeVideoCallback = new Callback<ResponseBody>() {
        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

            if (!response.isSuccessful()) {
                int responseCode = response.code();
                if(responseCode == 504) { // 504 Unsatisfiable Request (only-if-cached)
//                    errorTextView.setText("Can't load data.\nCheck your network connection.");
//                    errorLinearLayout.setVisibility(View.VISIBLE);
                }
                return;
            }

            okhttp3.Response rawResponse = response.raw();
            if (rawResponse != null) {
                int code = rawResponse.code();
                switch (code) {
                    case 204:
                        // No Content
                        BusProvider.getInstance().post(new VideoLikedEvent());

                        HashMap<String, Object> map = new HashMap<>();
                        map.put(EventMapKeys.NAME, video.getName());
                        map.put(EventMapKeys.DURATION, video.getDuration());
                        map.put(EventMapKeys.VIDEO_ID, videoId);

                        Event event = new Event(EventNames.VIDEO_LIKED, map);
                        EventLogger.logEvent(event);

                        relatedVideosAdapter.setIsLikeOn(true);
                        ImageView imageView = (ImageView) videosRecyclerView.getLayoutManager().findViewByPosition(0).findViewById(R.id.like_iv);
                        imageView.setImageResource(R.drawable.ic_like_on);
                        break;
                    case 400:
                        // If the video is owned by the authenticated user
                        break;
                    case 403:
                        // If the authenticated user is not allowed to like videos
                        break;
                }
            }
        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
            NetworkLogUtility.logFailure(call, t);

            if(t instanceof ConnectException || t instanceof UnknownHostException){
                Snackbar.make(getActivity().findViewById(R.id.main_content),
                        TrestleUtility.getFormattedText("Network connection is unavailable.", font, 16),
                        Snackbar.LENGTH_LONG)
                        .show();
            }
        }
    };

    private Callback<ResponseBody> unlikeVideoCallback = new Callback<ResponseBody>() {
        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

            if (!response.isSuccessful()) {
                int responseCode = response.code();
                if(responseCode == 504) { // 504 Unsatisfiable Request (only-if-cached)
//                    errorTextView.setText("Can't load data.\nCheck your network connection.");
//                    errorLinearLayout.setVisibility(View.VISIBLE);
                }
                return;
            }

            okhttp3.Response rawResponse = response.raw();
            if (rawResponse != null) {
                int code = rawResponse.code();
                switch (code) {
                    case 204:
                        // No Content
                        BusProvider.getInstance().post(new VideoLikedEvent());

                        HashMap<String, Object> map = new HashMap<>();
                        map.put(EventMapKeys.NAME, video.getName());
                        map.put(EventMapKeys.DURATION, video.getDuration());
                        map.put(EventMapKeys.VIDEO_ID, videoId);

                        Event event = new Event(EventNames.VIDEO_DISLIKED, map);
                        EventLogger.logEvent(event);

                        relatedVideosAdapter.setIsLikeOn(false);
                        ImageView imageView = (ImageView) videosRecyclerView.getLayoutManager().findViewByPosition(0).findViewById(R.id.like_iv);
                        imageView.setImageResource(R.drawable.ic_like_off);
                        break;
                    case 403:
                        // If the authenticated user is not allowed to like videos
                        break;
                }
            }
        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
            NetworkLogUtility.logFailure(call, t);

            if(t instanceof ConnectException || t instanceof UnknownHostException){
                Snackbar.make(getActivity().findViewById(R.id.main_content),
                        TrestleUtility.getFormattedText("Network connection is unavailable.", font, 16),
                        Snackbar.LENGTH_LONG)
                        .show();
            }
        }
    };

    private Callback<ResponseBody> addVideoToWatchLaterCallback = new Callback<ResponseBody>() {
        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
            if (!response.isSuccessful()) {
                int responseCode = response.code();
                if(responseCode == 504) { // 504 Unsatisfiable Request (only-if-cached)
//                    errorTextView.setText("Can't load data.\nCheck your network connection.");
//                    errorLinearLayout.setVisibility(View.VISIBLE);
                }
                return;
            }

            okhttp3.Response rawResponse = response.raw();
            if (rawResponse != null) {
                int code = rawResponse.code();
                switch (code) {
                    case 204:
                        // No Content
                        BusProvider.getInstance().post(new WatchLaterEvent());

                        relatedVideosAdapter.setIsWatchLaterOn(true);
                        ImageView imageView = (ImageView) videosRecyclerView.getLayoutManager().findViewByPosition(0).findViewById(R.id.watch_later_iv);
                        imageView.setImageResource(R.drawable.ic_watch_later_on);
                        break;
//                            case 400:
//                                // If the video is owned by the authenticated user
//                                break;
//                            case 403:
//                                // If the authenticated user is not allowed to like videos
//                                break;
                }
            }
        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
            NetworkLogUtility.logFailure(call, t);

            if(t instanceof ConnectException || t instanceof UnknownHostException){
                Snackbar.make(getActivity().findViewById(R.id.main_content),
                        TrestleUtility.getFormattedText("Network connection is unavailable.", font, 16),
                        Snackbar.LENGTH_LONG)
                        .show();
            }
        }
    };

    private Callback<ResponseBody> removeVideoFromWatchLaterCallback = new Callback<ResponseBody>() {
        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
            if (!response.isSuccessful()) {
                int responseCode = response.code();
                if(responseCode == 504) { // 504 Unsatisfiable Request (only-if-cached)
//                    errorTextView.setText("Can't load data.\nCheck your network connection.");
//                    errorLinearLayout.setVisibility(View.VISIBLE);
                }
                return;
            }

            okhttp3.Response rawResponse = response.raw();
            if (rawResponse != null) {
                int code = rawResponse.code();
                switch (code) {
                    case 204:
                        // No Content
                        BusProvider.getInstance().post(new WatchLaterEvent());

                        relatedVideosAdapter.setIsWatchLaterOn(false);
                        ImageView imageView = (ImageView) videosRecyclerView.getLayoutManager().findViewByPosition(0).findViewById(R.id.watch_later_iv);
                        imageView.setImageResource(R.drawable.ic_watch_later_off);
                        break;
//                            case 403:
//                                // If the authenticated user is not allowed to like videos
//                                break;
                }
            }
        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
            NetworkLogUtility.logFailure(call, t);

            if(t instanceof ConnectException || t instanceof UnknownHostException){
                Snackbar.make(getActivity().findViewById(R.id.main_content),
                        TrestleUtility.getFormattedText("Network connection is unavailable.", font, 16),
                        Snackbar.LENGTH_LONG)
                        .show();
            }
        }
    };

    // endregion

    // region Constructors
    public VideoDetailsFragment() {
    }
    // endregion

    // region Factory Methods
    public static VideoDetailsFragment newInstance(Bundle extras) {
        VideoDetailsFragment fragment = new VideoDetailsFragment();
        fragment.setArguments(extras);
        return fragment;
    }

    public static VideoDetailsFragment newInstance() {
        VideoDetailsFragment fragment = new VideoDetailsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }
    // endregion

    // region Lifecycle Methods
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retain this fragment across configuration changes.
        setRetainInstance(true);

        if (getArguments() != null) {
            video = (Video) getArguments().get(LikedVideosFragment.KEY_VIDEO);
//            mTransitionName = getArguments().getString("TRANSITION_KEY");
        }

        AccessToken token = LoopPrefs.getAccessToken(getActivity());
        vimeoService = ServiceGenerator.createService(
                VimeoService.class,
                VimeoService.BASE_URL,
                token);

        setHasOptionsMenu(true);

        font = FontCache.getTypeface("Ubuntu-Medium.ttf", getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_video_details, container, false);
        ButterKnife.bind(this, rootView);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

//        ViewCompat.setTransitionName(mVideoThumbnailImageView, mTransitionName);

        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("");
        }

        if (video != null) {
            setUpVideoThumbnail();

            long id = video.getId();
            if (id != -1L) {
                videoId = id;

                layoutManager = new LinearLayoutManager(getActivity());
                videosRecyclerView.setLayoutManager(layoutManager);
                relatedVideosAdapter = new RelatedVideosAdapter(video);
                relatedVideosAdapter.setOnItemClickListener(this);
                relatedVideosAdapter.setOnReloadClickListener(this);
                relatedVideosAdapter.setOnLikeClickListener(this);
                relatedVideosAdapter.setOnWatchLaterClickListener(this);
                relatedVideosAdapter.setOnCommentsClickListener(this);
                relatedVideosAdapter.setOnInfoClickListener(this);
                relatedVideosAdapter.addHeader();
                videosRecyclerView.setItemAnimator(new SlideInUpAnimator());
                videosRecyclerView.setAdapter(relatedVideosAdapter);

                // Pagination
                videosRecyclerView.addOnScrollListener(recyclerViewOnScrollListener);

                Call findRelatedVideosCall = vimeoService.findRelatedVideos(videoId, currentPage, PAGE_SIZE);
                calls.add(findRelatedVideosCall);
                findRelatedVideosCall.enqueue(getRelatedVideosFirstFetchCallback);
            }
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        BusProvider.getInstance().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        BusProvider.getInstance().unregister(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        removeListeners();
        ButterKnife.unbind(this);
    }

    // endregion

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.video_details_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.share:
                if (video != null) {
//                    EventLogger.fire(ProductShareEvent.start(mProduct.getId()));

                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("text/plain");
                    sendIntent.putExtra(Intent.EXTRA_TEXT,
                            String.format("I found this on Loop. Check it out.\n\n%s\n\n%s", video.getName(), video.getLink()));

                    String title = getResources().getString(R.string.share_this_video);
                    Intent chooser = Intent.createChooser(sendIntent, title);

                    if (sendIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                        startActivityForResult(chooser, VIDEO_SHARE_REQUEST_CODE);
                    }
                }
                return true;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case VIDEO_SHARE_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    if (video != null) {
//                        EventLogger.fire(ProductShareEvent.submit(mProduct.getId()));
                    }
                } else if (resultCode == Activity.RESULT_CANCELED) {
                }
                break;
            default:
                break;
        }
    }

    // region RelatedVideosAdapter.OnItemClickListener Methods
    @Override
    public void onItemClick(int position, View view) {
        Video video = relatedVideosAdapter.getItem(position);
        if (video != null) {
            Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);

            Bundle bundle = new Bundle();
            bundle.putParcelable(LikedVideosFragment.KEY_VIDEO, video);
            intent.putExtras(bundle);

            Pair<View, String> p1 = Pair.create(view.findViewById(R.id.video_thumbnail_iv), "videoTransition");
//                Pair<View, String> p2 = Pair.create((View) view.findViewById(R.id.title_tv), "titleTransition");
//                Pair<View, String> p3 = Pair.create((View) view.findViewById(R.id.subtitle_tv), "subtitleTransition");
//        Pair<View, String> p4 = Pair.create((View)view.findViewById(R.id.uploaded_tv), "uploadedTransition");

//                ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
//                        p1, p2, p3);

            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                    p1);


//            ActivityCompat.startActivity(getActivity(), intent, options.toBundle());

            startActivity(intent);
        }
    }
    // endregion

    // region RelatedVideosAdapter.OnReloadClickListener Methods

    @Override
    public void onReloadClick() {
        relatedVideosAdapter.updateFooter(RelatedVideosAdapter.FooterType.LOAD_MORE);

        Call findRelatedVideosCall = vimeoService.findRelatedVideos(videoId, currentPage, PAGE_SIZE);
        calls.add(findRelatedVideosCall);
        findRelatedVideosCall.enqueue(getRelatedVideosNextFetchCallback);
    }

    // endregion

    // region RelatedVideosAdapter.OnLikeClickListener Methods
    @Override
    public void onLikeClick(final ImageView imageView) {
        if (relatedVideosAdapter.isLikeOn()) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity(), R.style.DialogTheme);
            alertDialogBuilder.setMessage("Are you sure you want to unlike this video?");
            alertDialogBuilder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Call unlikeVideoCall = vimeoService.unlikeVideo(String.valueOf(videoId));
                    calls.add(unlikeVideoCall);
                    unlikeVideoCall.enqueue(unlikeVideoCallback);
                }
            });
            alertDialogBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                }
            });
            alertDialogBuilder.show();
        } else {
            Call likeVideoCall = vimeoService.likeVideo(String.valueOf(videoId));
            calls.add(likeVideoCall);
            likeVideoCall.enqueue(likeVideoCallback);
        }
    }
    // endregion

    // region RelatedVideosAdapter.OnWatchLaterClickListener Methods
    @Override
    public void onWatchLaterClick(final ImageView imageView) {
        if (relatedVideosAdapter.isWatchLaterOn()) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity(), R.style.DialogTheme);
            alertDialogBuilder.setMessage("Are you sure you want to remove this video from your Watch Later collection?");
            alertDialogBuilder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Call removeVideoFromWatchLaterCall = vimeoService.removeVideoFromWatchLater(String.valueOf(videoId));
                    calls.add(removeVideoFromWatchLaterCall);
                    removeVideoFromWatchLaterCall.enqueue(removeVideoFromWatchLaterCallback);
                }
            });
            alertDialogBuilder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                }
            });
            alertDialogBuilder.show();
        } else {
            Call addVideoToWatchLaterCall = vimeoService.addVideoToWatchLater(String.valueOf(videoId));
            calls.add(addVideoToWatchLaterCall);
            addVideoToWatchLaterCall.enqueue(addVideoToWatchLaterCallback);
        }
    }
    // endregion

    // region RelatedVideosAdapter.OnCommentsClickListener Methods
    @Override
    public void onCommentsClick() {

        Intent intent = new Intent(getActivity(), VideoCommentsActivity.class);

        Bundle bundle = new Bundle();
        bundle.putParcelable(LikedVideosFragment.KEY_VIDEO, video);
        intent.putExtras(bundle);

        startActivity(intent);
    }
    // endregion

    // region RelatedVideosAdapter.OnInfoClickListener Methods
    @Override
    public void onInfoClick(final ImageView imageView) {
        if(isInfoExpanded){
            isInfoExpanded = false;
            imageView.setImageResource(R.drawable.ic_keyboard_arrow_down);
        } else {
            isInfoExpanded = true;
            imageView.setImageResource(R.drawable.ic_keyboard_arrow_up);
        }
    }
    // endregion

    // region Otto Methods
    @Subscribe
    public void onSearchPerformed(SearchPerformedEvent event) {
        String query = event.getQuery();
        if (!TextUtils.isEmpty(query)) {
            launchSearchActivity(query);
        }
    }
    // endregion

    // region Helper Methods

    private void setUpVideoThumbnail() {
        Pictures pictures = video.getPictures();
        if (pictures != null) {
            List<Size> sizes = pictures.getSizes();
            if (sizes != null && sizes.size() > 0) {
                Size size = sizes.get(sizes.size() - 1);
                if (size != null) {
                    String link = size.getLink();
                    if (!TextUtils.isEmpty(link)) {
                        Glide.with(getActivity())
                                .load(link)
//                                .placeholder(R.drawable.ic_placeholder)
//                                .error(R.drawable.ic_error)
                                .into(videoThumbnailImageView);
                    }
                }
            }
        }
    }

    private void loadMoreItems() {
        isLoading = true;

        currentPage += 1;

        Call findRelatedVideosCall = vimeoService.findRelatedVideos(videoId, currentPage, PAGE_SIZE);
        calls.add(findRelatedVideosCall);
        findRelatedVideosCall.enqueue(getRelatedVideosNextFetchCallback);
    }

    private void launchSearchActivity(String query) {
        Intent intent = new Intent(getContext(), SearchableActivity.class);
        intent.setAction(Intent.ACTION_SEARCH);
//        intent.putExtra(SearchManager.QUERY, query);
        Bundle bundle = new Bundle();
        bundle.putString(SearchManager.QUERY, query);
        intent.putExtras(bundle);
        getContext().startActivity(intent);
    }

//    private void showReloadSnackbar(String message) {
//        Snackbar.make(getActivity().findViewById(android.R.id.content),
//                message,
//                Snackbar.LENGTH_INDEFINITE)
//                .setAction("Reload", reloadOnClickListener)
////                                .setActionTextColor(Color.RED)
//                .show();
//    }

    private void removeListeners() {
        videosRecyclerView.removeOnScrollListener(recyclerViewOnScrollListener);
    }
    // endregion
}
