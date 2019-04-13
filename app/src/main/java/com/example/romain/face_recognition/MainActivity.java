package com.example.romain.face_recognition;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.*;
import android.os.*;
import android.support.v4.content.FileProvider;
import android.view.*;
import android.graphics.*;
import android.widget.*;
import android.provider.*;
import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;

public class MainActivity extends Activity {

    private final int PICK_IMAGE = 1;
    private final int REQUEST_IMAGE_CAPTURE = 1;
    private ProgressDialog detectionProgressDialog;
    private Uri mCurrentPhotoPath;

    // Replace `<API endpoint>` with the Azure region associated with
// your subscription key. For example,
// apiEndpoint = "https://westcentralus.api.cognitive.microsoft.com/face/v1.0"
    private final String apiEndpoint = "https://francecentral.api.cognitive.microsoft.com/face/v1.0";

    // Replace `<Subscription Key>` with your subscription key.
// For example, subscriptionKey = "0123456789abcdef0123456789ABCDEF"
    private final String subscriptionKey = "51b964794ebb418492fcabba535d5f7a";

    private final FaceServiceClient faceServiceClient =
            new FaceServiceRestClient(apiEndpoint, subscriptionKey);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button2 = findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    // Create the File where the photo should go
                    File photoFile = null;
                    try {
                        photoFile = createImageFile();
                    } catch (IOException ex) {
                        // Error occurred while creating the File
                    }
                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        Uri photoURI = FileProvider.getUriForFile(getApplicationContext(),
                                "com.example.android.fileprovider",
                                photoFile);
                        mCurrentPhotoPath = photoURI;
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                    }
                }
            }
        });

        detectionProgressDialog = new ProgressDialog(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK)
        {
            try {
                // Recover and display photo
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), mCurrentPhotoPath);
                ImageView imageView = findViewById(R.id.imageView1);
                imageView.setImageBitmap(bitmap);

                // Comment out for tutorial
                detectAndFrame(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    // Detect faces by uploading a face image.
// Frame faces after detection.
    private void detectAndFrame(final Bitmap imageBitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(outputStream.toByteArray());

        AsyncTask<InputStream, String, Face[]> detectTask =
                new AsyncTask<InputStream, String, Face[]>() {
                    String exceptionMessage = "";

                    @Override
                    protected Face[] doInBackground(InputStream... params) {
                        try {
                            publishProgress("Detecting...");
                            Face[] result = faceServiceClient.detect(
                                    params[0],
                                    true,         // returnFaceId
                                    false,        // returnFaceLandmarks
                                    null          // returnFaceAttributes:
                                /* new FaceServiceClient.FaceAttributeType[] {
                                    FaceServiceClient.FaceAttributeType.Age,
                                    FaceServiceClient.FaceAttributeType.Gender }
                                */
                            );

                            if (result == null){
                                publishProgress(
                                        "Detection Finished. Nothing detected");
                                return null;
                            }
                            publishProgress(String.format(
                                    "Detection Finished. %d face(s) detected",
                                    result.length));

                            return result;
                        } catch (Exception e) {
                            exceptionMessage = String.format(
                                    "Detection failed: %s", e.getMessage());
                            return null;
                        }
                    }

                    @Override
                    protected void onPreExecute() {
                        //TODO: show progress dialog
                        detectionProgressDialog.show();
                    }
                    @Override
                    protected void onProgressUpdate(String... progress) {
                        //TODO: update progress
                        detectionProgressDialog.setMessage(progress[0]);
                    }
                    @Override
                    protected void onPostExecute(Face[] result) {
                        //TODO: update face frames
                        detectionProgressDialog.dismiss();

                        if(!exceptionMessage.equals("")){
                            showError(exceptionMessage);
                        }
                        if (result == null) return;

                        ImageView imageView = findViewById(R.id.imageView1);
                        imageView.setImageBitmap(
                                drawFaceRectanglesOnBitmap(imageBitmap, result));

                        imageBitmap.recycle();

                        compare(result);
                    }
                };

        detectTask.execute(inputStream);
    }

    private void compare(Face[] faces)
    {
        // Fetch all photos in gallery (until we can connect to firebase)
        Uri uri;
        Cursor cursor;
        int column_index;
        String path = null,sortOrder;
        ArrayList<String> imageList = new ArrayList<>();
        uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.MediaColumns.DATA };
        //DATA is the path to the corresponding image. We only need this for loading //image into a recyclerview

        sortOrder = MediaStore.Images.ImageColumns.DATE_ADDED + " DESC";
        //This sorts all images such that recent ones appear first

        cursor = getContentResolver().query(uri, projection, null,null, sortOrder);

        try{
            if (null != cursor) {
                column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                while (cursor.moveToNext()) {
                    path = cursor.getString(column_index);
                    imageList.add(path);
                }
                cursor.close();
            }
            //imageList gets populated with paths to images by here
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        for (String imagePath :
                imageList)
        {
            try
            {
                Face[] result = faceServiceClient.detect(
                        imagePath,
                        true,         // returnFaceId
                        false,        // returnFaceLandmarks
                        null          // returnFaceAttributes:
                                /* new FaceServiceClient.FaceAttributeType[] {
                                    FaceServiceClient.FaceAttributeType.Age,
                                    FaceServiceClient.FaceAttributeType.Gender }
                                */
                );

                for (Face faceStored :
                        result) {
                    for (Face faceInput:
                         faces) {
                        VerifyResult VResult = faceServiceClient.verify(faceInput.faceId, faceStored.faceId);

                        if(VResult.isIdentical)
                        {
                            Toast.makeText(MainActivity.this, "Authorization granted",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                }

                Toast.makeText(MainActivity.this, "Authorization denied",
                        Toast.LENGTH_LONG).show();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }


        }


    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }})
                .create().show();
    }
    private static Bitmap drawFaceRectanglesOnBitmap(
            Bitmap originalBitmap, Face[] faces) {
        Bitmap bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);
        paint.setStrokeWidth(10);
        if (faces != null) {
            for (Face face : faces) {
                FaceRectangle faceRectangle = face.faceRectangle;
                canvas.drawRect(
                        faceRectangle.left,
                        faceRectangle.top,
                        faceRectangle.left + faceRectangle.width,
                        faceRectangle.top + faceRectangle.height,
                        paint);
            }
        }
        return bitmap;
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        return image;
    }
}
