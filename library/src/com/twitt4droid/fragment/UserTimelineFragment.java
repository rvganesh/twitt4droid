/*
 * Copyright 2014 Daniel Pedraza-Arcega
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
package com.twitt4droid.fragment;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.twitt4droid.R;
import com.twitt4droid.Resources;
import com.twitt4droid.Twitt4droid;
import com.twitt4droid.data.dao.UserDAO;
import com.twitt4droid.data.dao.UserTimelineDAO;
import com.twitt4droid.data.dao.impl.DAOFactory;
import com.twitt4droid.util.Images.ImageLoader;
import com.twitt4droid.util.Strings;

import twitter4j.AsyncTwitter;
import twitter4j.TwitterAdapter;
import twitter4j.TwitterException;
import twitter4j.TwitterMethod;
import twitter4j.User;

import java.util.List;

/**
 * Shows the 20 most recent statuses posted from the given user.
 * 
 * @author Daniel Pedraza-Arcega
 * @since version 1.0
 */
public class UserTimelineFragment extends TimelineFragment {

    private static final String USERNAME_ARG = "USERNAME";

    private static final String TAG = UserTimelineFragment.class.getSimpleName();

    private ImageView userProfileBannerImage;
    private ImageView userProfileImage;
    private TextView userUsername;
    private TextView userScreenName;
    private AsyncTwitter twitter;

    /**
     * Creates a UserTimelineFragment.
     * 
     * @param username a username.
     * @param enableDarkTheme if the dark theme is enabled.
     * @return a new UserTimelineFragment.
     */
    public static UserTimelineFragment newInstance(String username, boolean enableDarkTheme) {
        UserTimelineFragment fragment = new UserTimelineFragment();
        Bundle args = new Bundle();
        args.putString(USERNAME_ARG, username);
        args.putBoolean(ENABLE_DARK_THEME_ARG, enableDarkTheme);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Creates a UserTimelineFragment.
     * 
     * @param username a username.
     * @return a new UserTimelineFragment.
     */
    public static UserTimelineFragment newInstance(String username) {
        return newInstance(username, false);
    }

    /** {@inheritDoc} */
    @Override
    protected UserStatusesLoaderTask initStatusesLoaderTask() {
        return new UserStatusesLoaderTask(new DAOFactory(getActivity().getApplicationContext()).getUserTimelineDAO(), getUsername());
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initStatusesLoaderTask().execute();
        if (Resources.isConnectedToInternet(getActivity())) twitter.showUser(getUsername());
        else new CachedUserLoaderTask(getUsername()).execute();
    }

    /** {@inheritDoc} */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.twitt4droid_user_timeline, container, false);
        setUpLayout(layout);
        return layout;
    }

    /** {@inheritDoc} */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        twitter = Twitt4droid.getAsyncTwitter(getActivity());
        setUpTwitter();
    }

    /** Sets up twitter callbacks. */
    private void setUpTwitter() {
        twitter.addListener(new TwitterAdapter() {

            @Override
            public void gotUserDetail(final User user) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            if (user != null) setUpUser(user);
                        }
                    });
                }
            }

            @Override
            public void onException(TwitterException te, TwitterMethod method) {
                Log.e(TAG, "Twitter error in" + method, te);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        
                        @Override
                        public void run() {
                            Toast.makeText(getActivity().getApplicationContext(), 
                                    R.string.twitt4droid_error_message, 
                                    Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
                }
            }
        });
    }

    /**
     * Sets up the user GUI.
     * 
     * @param user the user.
     */
    private void setUpUser(User user) {
        userScreenName.setText(user.getName());
        if (!Strings.isNullOrBlank(user.getProfileBannerURL())) {
            new ImageLoader(getActivity())
                .setLoadingColorId(R.color.twitt4droid_no_image_background)
                .setImageView(userProfileBannerImage)
                .execute(user.getProfileBannerURL());
        }
        if (!Strings.isNullOrBlank(user.getProfileImageURL())) {
            new ImageLoader(getActivity())
                .setLoadingColorId(R.color.twitt4droid_no_image_background)
                .setImageView(userProfileImage)
                .execute(user.getProfileImageURL());
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void setUpLayout(View layout) {
        super.setUpLayout(layout);
        userProfileBannerImage = (ImageView) layout.findViewById(R.id.user_banner_image);
        userProfileImage = (ImageView) layout.findViewById(R.id.user_profile_image);
        userUsername = (TextView) layout.findViewById(R.id.user_username);
        userScreenName = (TextView) layout.findViewById(R.id.user_screen_name);
        userUsername.setText(getString(R.string.twitt4droid_username_format, getUsername()));
    }

    /** {@inheritDoc} */
    @Override
    public int getResourceTitle() {
        return R.string.twitt4droid_user_timeline_fragment_title;
    }

    /** {@inheritDoc} */
    @Override
    public int getResourceHoloLightIcon() {
        return R.drawable.twitt4droid_ic_person_holo_light;
    }

    /** {@inheritDoc} */
    @Override
    public int getResourceHoloDarkIcon() {
        return R.drawable.twitt4droid_ic_person_holo_dark;
    }

    /** @return the username. */
    public String getUsername() {
        return getArguments().getString(USERNAME_ARG);
    }

    /**
     * Loads a cached user from datastore asynchronously.
     * 
     * @author Daniel Pedraza-Arcega
     * @since version 1.0
     */
    private class CachedUserLoaderTask extends AsyncTask<Void, Void, User> {

        private final String username;
        private final UserDAO userDAO;

        /**
         * Creates a CachedUserLoaderTask.
         * 
         * @param username the username
         */
        private CachedUserLoaderTask(String username) {
            this.username = username;
            userDAO = new DAOFactory(getActivity()).getUserDAO();
        }

        /** {@inheritDoc} */
        @Override
        protected User doInBackground(Void... params) {
            return userDAO.fetchByScreenName(username);
        }

        /** {@inheritDoc} */
        @Override
        protected void onPostExecute(User user) {
            if (user != null) setUpUser(user);
        }
    }

    /**
     * Loads twitter statuses asynchronously.
     * 
     * @author Daniel Pedraza-Arcega
     * @since version 1.0
     */
    private class UserStatusesLoaderTask extends StatusesLoaderTask {

        private final String username;

        /**
         * Creates a UserStatusesLoaderTask.
         * 
         * @param timelineDao a TimelineDAO.
         * @param username a username.
         */
        protected UserStatusesLoaderTask(UserTimelineDAO timelineDao, String username) {
            super(timelineDao);
            this.username = username;
        }

        /** {@inheritDoc} */
        @Override
        protected List<twitter4j.Status> loadTweetsInBackground() throws TwitterException {
            UserTimelineDAO timelineDAO = (UserTimelineDAO) getDAO();
            List<twitter4j.Status> statuses = null;
            if (isConnectedToInternet()) {
                statuses = getTwitter().getUserTimeline(username);
                // TODO: update statuses instead of deleting all previous statuses and save new ones.
                timelineDAO.deleteAll();
                timelineDAO.save(statuses);
            } else statuses = timelineDAO.fetchListByScreenName(username);
            return statuses;
        }
    }
}