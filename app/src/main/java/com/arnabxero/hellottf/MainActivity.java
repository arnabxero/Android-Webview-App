package com.arnabxero.hellottf;

import android.Manifest;

import androidx.annotation.NonNull;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final int FILE_CHOOSER_RESULT_CODE = 1;
    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> uploadMessage;
    private static final int REQUEST_STORAGE_PERMISSION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestStoragePermission();
        // Initialize webView and progressBar
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progressBar);

        // Initially hide the WebView
        webView.setVisibility(View.INVISIBLE);

        // Set a WebViewClient to handle page loading events
        webView.setWebViewClient(new MyWebClient());

        // Enable JavaScript in WebView
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        // Enable file access
        webSettings.setAllowFileAccess(true);

        // Enable local storage
        webSettings.setDomStorageEnabled(true);

        // Load the URL
//        webView.loadUrl("https://app.teamtigerforce.com");
        // Handle the incoming URL
        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();

        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String url = data.toString();
            // Check if the URL is from the specified domain
            if(url.startsWith("https://app.teamtigerforce.com")) {
                webView.loadUrl(url); // Load the URL in the WebView
            }
        } else {
            // Load a default URL or homepage
            webView.loadUrl("https://app.teamtigerforce.com");
        }

        // Set up WebView to handle downloads
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (hasStoragePermission()) {
                    new DownloadTask().execute(url);
                } else {
                    requestStoragePermission();
                }
            }
        });


        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength){
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                    return;
                }

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));
                request.setDescription("Downloading File...");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(getApplicationContext(), "Downloading...", Toast.LENGTH_SHORT).show();
                registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
            BroadcastReceiver onComplete = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Toast.makeText(getApplicationContext(), "Download Complete", Toast.LENGTH_SHORT).show();
                }
            };
        });


        // Set up WebView's WebChromeClient to handle file uploads
        webView.setWebChromeClient(new WebChromeClient() {
            // For Android 5.0+
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }
                uploadMessage = filePathCallback;

                // Check for storage permission
                if (!hasStoragePermission()) {
                    requestStoragePermission();
                    return false;
                }

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE);
                } catch (ActivityNotFoundException e) {
                    uploadMessage = null;
                    Toast.makeText(MainActivity.this, "Cannot open file chooser", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }

        });
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }


//    private boolean hasStoragePermission() {
//        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
//    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            }, REQUEST_STORAGE_PERMISSION);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_STORAGE_PERMISSION);
        }
    }

//    private void requestStoragePermission() {
//        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
//    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0) {
                boolean allPermissionsGranted = true;
                for (int grantResult : grantResults) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false;
                        break;
                    }
                }

                if (allPermissionsGranted) {
                    // Permission granted, handle the action that required permission
                } else {
                    Toast.makeText(this, "Storage permission is required.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//
//        if (requestCode == REQUEST_STORAGE_PERMISSION) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // Permission granted, handle the download
//            } else {
//                Toast.makeText(this, "Storage permission is required to download files.", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }

    private void handleDataUriDownload(String base64Uri) {
        byte[] decodedData = decodeBase64Data(base64Uri);

        if (decodedData != null) {
            try {
                // Specify the download folder and filename
                String filename = "downloaded_file.png";
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

                // Create a File object for the download destination
                File destinationFile = new File(downloadsDir, filename);

                // Write the decoded data to the destination file
                FileOutputStream outputStream = new FileOutputStream(destinationFile);
                try {
                    outputStream.write(decodedData);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                outputStream.close();

                // Notify the DownloadManager to download the saved file
                Uri fileUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", destinationFile);

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(base64Uri));
                request.setDestinationUri(fileUri);

                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                downloadManager.enqueue(request);
            } catch (IOException e) {
                e.printStackTrace();
                // Handle IO errors
            }
        } else {
            // Handle decoding errors here
            Toast.makeText(this, "Failed to decode Base64 data.", Toast.LENGTH_SHORT).show();
        }
    }


    // Implement your Base64 decoding logic here
    private byte[] decodeBase64Data(String base64Uri) {
        try {
            // Remove the "data:" prefix from the data URI
            String base64Data = base64Uri.replaceFirst("data:[^;]*;[^,]*,", "");

            // Decode the Base64 data
            return android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            // Handle decoding errors, such as invalid Base64 data
            return null;
        }
    }

    private class DownloadTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... urls) {
            if (urls.length > 0) {
                String url = urls[0];
                byte[] decodedData = decodeBase64Data(url);

                if (decodedData != null) {
                    try {
                        // Specify the download folder and filename
                        String filename = "downloaded_file.png";
                        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

                        // Create a File object for the download destination
                        File destinationFile = new File(downloadsDir, filename);

                        // Write the decoded data to the destination file
                        FileOutputStream outputStream = new FileOutputStream(destinationFile);
                        outputStream.write(decodedData);
                        outputStream.close();

                        // Notify the DownloadManager to download the saved file
                        Uri fileUri = FileProvider.getUriForFile(MainActivity.this, getApplicationContext().getPackageName() + ".provider", destinationFile);

                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                        request.setDestinationUri(fileUri);

                        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        downloadManager.enqueue(request);

                        return true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                // Handle download success
                Toast.makeText(MainActivity.this, "Download started successfully.", Toast.LENGTH_SHORT).show();
            } else {
                // Handle download failure
                Toast.makeText(MainActivity.this, "Download failed.", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (uploadMessage == null)
                return;

            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
            }

            uploadMessage.onReceiveValue(results);
            uploadMessage = null;
        }
    }

    private class MyWebClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            // Hide the progress bar
            progressBar.setVisibility(View.GONE);

            // Make the WebView visible
            webView.setVisibility(View.VISIBLE);

            super.onPageFinished(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return super.shouldOverrideUrlLoading(view, request);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.isFocused() && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    // Handle permissions request results
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // Call super method
//
//        if (requestCode == 2) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // Permission granted, you can reattempt the download
//            } else {
//                Toast.makeText(this, "Permission denied. Cannot download the file.", Toast.LENGTH_SHORT).show();
//            }
//        }
//    }


}
