package com.projectbored.app;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class StoryUpload extends AppCompatActivity {
    private static final String TAG = ShowStory.class.getSimpleName();  //for debugging purposes

    EditText caption;

    private StorageReference mStorageRef;
    private DatabaseReference mDataRef;

    String mCurrentPhotoPath;
    String storyKey;

    Bundle storySettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.projectbored.app.R.layout.activity_story_upload);
        mStorageRef = FirebaseStorage.getInstance().getReference();
        mDataRef = FirebaseDatabase.getInstance().getReference();

        caption = (EditText)findViewById(R.id.story_caption);

        storySettings = getIntent().getExtras();

        storyKey = mDataRef.child("stories").push().getKey();

        dispatchTakePictureIntent();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(com.projectbored.app.R.menu.add_story_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == com.projectbored.app.R.id.option_upload_story) {
            uploadStoryData();
        }
        return true;
    }

    static final int REQUEST_IMAGE_CAPTURE = 1;
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try{
                photoFile = createImageFile();
            }catch (IOException ex) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Camera failed. Please try again later.")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                builder.create().show();
            }
            if (photoFile != null) {
                Uri photoUri = FileProvider.getUriForFile(this, "com.projectbored.app.fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
        //    Bundle extras= data.getExtras();
        //    Bitmap imageBitmap = (Bitmap)extras.get("data");
            ((ImageView) findViewById(com.projectbored.app.R.id.story_image)).setImageBitmap(BitmapFactory.decodeFile(mCurrentPhotoPath));
            uploadImage();
        }
    }

    private File createImageFile () throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "IMG_" + timestamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
               imageFileName,".jpg",storageDir
        );

        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void uploadImage () {
        Uri file = Uri.fromFile(new File(mCurrentPhotoPath));
        Log.d(TAG, "1");

        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("image/jpg")
                .build();
        Log.d(TAG, "2");

        UploadTask uploadTask = mStorageRef.child(file.getLastPathSegment()).putFile(file, metadata);
        Log.d(TAG, "3");

        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
                builder.setMessage("Upload failed. Please try again later.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent i = new Intent(StoryUpload.this, MapsActivityCurrentPlace.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(i);
                            }
                        });
                builder.create().show();
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                uploadImageData(taskSnapshot);
                Log.d(TAG, "6");
            }
        });
    }
    private void uploadImageData(UploadTask.TaskSnapshot taskSnapshot) {
        final Uri PHOTO_URI = taskSnapshot.getMetadata().getDownloadUrl();
        Log.d(TAG, "4");

        if(PHOTO_URI == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
            builder.setMessage("Upload failed. Please try again later.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent i = new Intent(StoryUpload.this, MapsActivityCurrentPlace.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(i);
                        }
                    });
            builder.create().show();
        } else {
            mDataRef.child("stories").child(storyKey).child("URI").setValue(PHOTO_URI.toString());
            Log.d(TAG, "5");
        }
    }

    private void uploadStoryData () {
        Location storyLocation = new Location(LocationManager.GPS_PROVIDER);
        storyLocation.setLatitude(storySettings.getDouble("Latitude"));
        storyLocation.setLongitude(storySettings.getDouble("Longitude"));

        if (storyLocation != null) {
            String locationString = Double.toString(storyLocation.getLatitude())
                                    + ","
                                    + Double.toString(storyLocation.getLongitude());
            String keyLocationString = locationString.replace(".", "d");    //keys in Firebase cannot contain ".", so replace with "d"
            Story story = new Story(storyLocation, caption.getText().toString(), new Date());
            Map<String, Object> storyDetails = story.toMap();

            Map<String, Object> childUpdates = new HashMap<>();
            childUpdates.put("/stories/" + storyKey, storyDetails);
            childUpdates.put("/locations/" + keyLocationString, storyKey);
            mDataRef.updateChildren(childUpdates);

            Toast.makeText(this, "Story added!", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(this, MapsActivityCurrentPlace.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } else {
            Toast.makeText(this, "An error occurred.", Toast.LENGTH_SHORT).show();
        }

    }
}