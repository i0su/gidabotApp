package com.github.gidabotapp.view;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.github.gidabotapp.R;
import com.github.gidabotapp.repository.QNode;

import org.ros.android.RosActivity;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

public class MainActivity extends RosActivity {
    private QNode qNode;
    NodeConfiguration nodeConfiguration;
    AlertDialog errorAlert;

    public MainActivity() {
        super("GidabotApp", "GidabotApp");
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.qNode = QNode.getInstance();
    }


    @Override
    protected void onStart() {
        super.onStart();
        setContentView(R.layout.activity_main);
        errorAlert = new AlertDialog.Builder(this).create();
        errorAlert.setTitle(getString(R.string.master_error_title));
        errorAlert.setMessage(getString(R.string.master_error_msg));
        errorAlert.setCancelable(false);
        errorAlert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.accept_btn),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                errorAlert.show();
            }
        }, 2000);

    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        errorAlert.dismiss();

        nodeConfiguration = NodeConfiguration.newPublic(getRosHostname());
        Log.i("HostName", getRosHostname());

        nodeConfiguration.setMasterUri(getMasterUri());
        Log.i("MasterUri", getMasterUri().toString());

        nodeMainExecutor.execute(qNode, nodeConfiguration);

        Intent intent = new Intent(getApplicationContext(), RouteSelectActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        qNode.shutdown();
    }
}