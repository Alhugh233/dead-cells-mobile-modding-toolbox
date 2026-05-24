package com.deadcells.modding;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AboutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 96, 48, 48);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText(R.string.about);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView space1 = new TextView(this);
        space1.setText("\n\n");
        layout.addView(space1);

        TextView dccm = new TextView(this);
        dccm.setText("DCCM — Dead Cells Core Modding");
        dccm.setTextSize(18);
        dccm.setGravity(Gravity.CENTER);
        layout.addView(dccm);

        TextView dccmDesc = new TextView(this);
        dccmDesc.setText("PAK / Atlas / CDB 解打包功能参考其实现\nhttps://github.com/dead-cells-core-modding/core\nLicensed under MIT");
        dccmDesc.setTextSize(14);
        dccmDesc.setGravity(Gravity.CENTER);
        layout.addView(dccmDesc);

        Button dccmBtn = new Button(this);
        dccmBtn.setText("访问 DCCM");
        dccmBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dead-cells-core-modding/core"));
            startActivity(intent);
        });
        layout.addView(dccmBtn);

        TextView space2 = new TextView(this);
        space2.setText("\n\n");
        layout.addView(space2);

        TextView alive = new TextView(this);
        alive.setText("alivecells");
        alive.setTextSize(18);
        alive.setGravity(Gravity.CENTER);
        layout.addView(alive);

        TextView aliveDesc = new TextView(this);
        aliveDesc.setText("PAK / Atlas 解打包功能参考其实现\nhttps://github.com/N3rdL0rd/alivecells\nLicensed under MIT");
        aliveDesc.setTextSize(14);
        aliveDesc.setGravity(Gravity.CENTER);
        layout.addView(aliveDesc);

        Button aliveBtn = new Button(this);
        aliveBtn.setText("访问 alivecells");
        aliveBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/N3rdL0rd/alivecells"));
            startActivity(intent);
        });
        layout.addView(aliveBtn);

        setContentView(layout);
    }
}
