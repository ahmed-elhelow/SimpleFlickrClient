package com.example.android.simpleflickrclient;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.android.simpleflickrclient.data.FlickrContract;
import com.example.android.simpleflickrclient.data.FlickrProvider;
import com.googlecode.flickrjandroid.Flickr;
import com.googlecode.flickrjandroid.REST;
import com.googlecode.flickrjandroid.photos.Extras;
import com.googlecode.flickrjandroid.photos.PhotoList;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class ImagesFragment extends Fragment {
    private final String LOG_TAG = ImagesFragment.class.getSimpleName();

    ImageListAdapter mImageAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    boolean recreateFlag = false;

    public ImagesFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        int db_elements_cnt = (int) FlickrProvider.getProfilesCount(getActivity());
        List<Integer> myList = Arrays.asList(new Integer[db_elements_cnt]);
        mImageAdapter =
                new ImageListAdapter(getActivity(), myList);

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        mSwipeRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeRefresh);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.blue, R.color.green,
                R.color.orange, R.color.purple);

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                recreateFlag = true;
                getActivity().getContentResolver().delete(
                        FlickrContract.FlickrImages.CONTENT_URI, null, null);

                FetchBasicDataTask fetchBasicDataTask = new FetchBasicDataTask();
                fetchBasicDataTask.execute();

                Toast.makeText(getActivity(), "Delete", Toast.LENGTH_SHORT).show();
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                }, 6000);
            }
        });

        GridView gridView = (GridView) rootView.findViewById(R.id.gridview_container);
        gridView.setAdapter(mImageAdapter);

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                Intent intent = new Intent(getActivity(), FullScreenActivity.class)
                        .putExtra("position", position);
                startActivity(intent);
            }
        });

        if (db_elements_cnt == 0) {
            recreateFlag = true;
            FetchBasicDataTask fetchBasicDataTask = new FetchBasicDataTask();
            fetchBasicDataTask.execute();
        }

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mImageAdapter.notifyDataSetChanged();
                handler.postDelayed(this, 60 * 1000);
            }
        }, 60 * 1000);

        return rootView;
    }

    public class ImageListAdapter extends ArrayAdapter {
        private Context context;
        private LayoutInflater inflater;

        List<Integer> myList;
        private int firstViewFlag;
        Set toDownload = new HashSet();

        public ImageListAdapter(Context context, List<Integer> myList) {
            super(context, R.layout.grid_item_image, myList);

            this.context = context;
            this.firstViewFlag = 0;
            this.myList = myList;
            inflater = LayoutInflater.from(context);
        }

        public void updateAdapter(List<Integer> myList) {
            this.myList = myList;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            GridView gridView = (GridView) parent;
            if (convertView != null && position == 0) {
                if (gridView.getFirstVisiblePosition() > 0) {
                    firstViewFlag = 0;
                    return convertView;
                } else {
                    if (firstViewFlag < 3) {
                        firstViewFlag++;
                        notifyDataSetChanged();
                    } else
                        return convertView;
                }
            }

            if (null == convertView) {
                convertView = inflater.inflate(R.layout.grid_item_image, parent, false);
            }

            ImageView imageView = (ImageView) convertView
                    .findViewById(R.id.grid_item_imageview);
            Cursor cursor = null;

            try {
                cursor = getActivity().getContentResolver()
                        .query(FlickrContract.FlickrImages.buildImagewithID(position),
                                null, null, null, null);

                if (cursor != null) {
                    cursor.moveToFirst();

                    int col_idx = cursor.getColumnIndex(
                            FlickrContract.FlickrImages.COLUMN_IMAGE);
                    byte[] image = null;
                    image = cursor.getBlob(col_idx);
                    cursor.close();

                    if (image == null) {
                        toDownload.add(position);
                        FetchImagesTask ImagesTask = new FetchImagesTask();
                        ImagesTask.execute(position);

                        Bitmap bmp = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(ContextCompat.getColor(context, R.color.white));
                        imageView.setImageBitmap(bmp);
                    } else {
                        Glide.with(context)
                                .load(image)
                                .asBitmap()
                                .into(imageView);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return imageView;
        }

        public class FetchImagesTask extends AsyncTask<Integer, Void, Integer> {
            private final String LOG_TAG = FetchImagesTask.class.getSimpleName();

            @Override
            protected Integer doInBackground(Integer... params) {
                int position = params[0];
                Cursor cursor = null;
                try {
                    cursor = getActivity().getContentResolver()
                            .query(FlickrContract.FlickrImages.buildImagewithID(position),
                                    null, null, null, null);

                    if (cursor != null) {
                        cursor.moveToFirst();

                        int col_idx = cursor.getColumnIndex(
                                FlickrContract.FlickrImages.COLUMN_IMAGE);
                        byte[] image = null;
                        image = cursor.getBlob(col_idx);

                        if (image == null) {
                            col_idx = cursor.getColumnIndex(
                                    FlickrContract.FlickrImages.COLUMN_IMAGE_url);
                            String url_str = cursor.getString(col_idx);
                            cursor.close();

                            URL url = new URL(url_str);
                            Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            bmp.compress(Bitmap.CompressFormat.PNG, 0, stream);
                            image = stream.toByteArray();

                            if (image != null) {
                                ContentValues flickrValues = new ContentValues();

                                flickrValues.put(FlickrContract.FlickrImages.COLUMN_IMAGE_ID, position);
                                flickrValues.put(FlickrContract.FlickrImages.COLUMN_IMAGE_url, url_str);
                                flickrValues.put(FlickrContract.FlickrImages.COLUMN_IMAGE, image);

                                Uri uri = getActivity().getContentResolver().insert(
                                        FlickrContract.FlickrImages.buildImagewithID(position),
                                        flickrValues);
                                return position;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (cursor != null && !cursor.isClosed())
                        cursor.close();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Integer position) {
                super.onPostExecute(position);
                if (position != null) {
                    toDownload.remove(position);
                    if (toDownload.size() % 2 == 0) {
                        firstViewFlag = 0;
                        notifyDataSetChanged();
                    }
                }
            }
        }
    }

    public class FetchBasicDataTask extends AsyncTask<Void, Void, Integer> {
        private final String LOG_TAG = FetchBasicDataTask.class.getSimpleName();

        @Override
        protected Integer doInBackground(Void... params) {

            int db_elements_cnt = 0;
            db_elements_cnt = (int) FlickrProvider.getProfilesCount(getActivity());

            if (db_elements_cnt == 0) {
                PhotoList list = null;
                try {
                    String apiKey = "77efa6dabf7ddd329ddb9fd37ab5ec76";
                    String sharedSecret = "5e3c8f3a57bc47fc";
                    Flickr f = new Flickr(apiKey, sharedSecret, new REST());
                    Set<String> extras = new HashSet<String>();
                    extras.add(Extras.URL_M);
                    list = f.getInterestingnessInterface().getList((String) null, extras, 500, 1);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (list != null) {
                    Vector<ContentValues> cVVector = new Vector<>(list.size());

                    for (int i = 0; i < list.size(); i++) {
                        ContentValues flickrValues = new ContentValues();
                        flickrValues.put(FlickrContract.FlickrImages.COLUMN_IMAGE_ID, i);
                        flickrValues.put(FlickrContract.FlickrImages.COLUMN_IMAGE_url,
                                list.get(i).getMediumUrl());

                        cVVector.add(flickrValues);
                    }

                    int inserted = 0;
                    if (cVVector.size() > 0) {
                        ContentValues[] cvArray = new ContentValues[cVVector.size()];
                        cVVector.toArray(cvArray);
                        inserted = getActivity().getContentResolver().bulkInsert(
                                FlickrContract.FlickrImages.CONTENT_URI, cvArray);
                    }
                    return inserted;
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Integer inserted) {
            super.onPostExecute(inserted);
            if (inserted != null)
                if (inserted > 0) {
                    List<Integer> myList = Arrays.asList(new Integer[inserted]);
                    mImageAdapter.updateAdapter(myList);
                    if (recreateFlag) {
                        getActivity().recreate();
                        recreateFlag = false;
                    } else
                        mImageAdapter.notifyDataSetChanged();
                    Toast.makeText(getActivity(), "Update", Toast.LENGTH_SHORT).show();
                }
        }
    }
}
