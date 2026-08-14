package com.salvadordetexto.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;

public class MainActivity extends Activity {
    AppUi ui;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ui=new AppUi(this);
        ui.splash();
        new Handler().postDelayed(()->ui.home(),1000);
    }
}
