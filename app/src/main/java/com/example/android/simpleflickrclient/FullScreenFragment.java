package com.example.android.simpleflickrclient;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.example.android.simpleflickrclient.data.FlickrContract;

public class FullScreenFragment extends Fragment {
    private final String LOG_TAG = FullScreenFragment.class.getSimpleName();

    public FullScreenFragment() {
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Intent mIntent = getActivity().getIntent();
        int position = mIntent.getIntExtra("position", 0);

        View rootView = inflater.inflate(R.layout.fragment_fullscreen, container, false);

        ImageView imageView = (ImageView) rootView.findViewById(R.id.full_screen_imageview);

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

                if (image != null) {
                    Glide.with(getActivity())
                            .load(image)
                            .asBitmap()
                            .into(imageView);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed())
                cursor.close();
        }

        return rootView;
    }
}
