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
        dccm.setText(getString(R.string.about_dccm_name));
        dccm.setTextSize(18);
        dccm.setGravity(Gravity.CENTER);
        layout.addView(dccm);

        TextView dccmDesc = new TextView(this);
        dccmDesc.setText(getString(R.string.about_dccm_desc));
        dccmDesc.setTextSize(14);
        dccmDesc.setGravity(Gravity.CENTER);
        layout.addView(dccmDesc);

        Button dccmBtn = new Button(this);
        dccmBtn.setText(getString(R.string.about_visit_dccm));
        dccmBtn.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://github.com/dead-cells-core-modding/core"))));
        layout.addView(dccmBtn);

        TextView space2 = new TextView(this);
        space2.setText("\n\n");
        layout.addView(space2);

        TextView alive = new TextView(this);
        alive.setText(getString(R.string.about_alive_name));
        alive.setTextSize(18);
        alive.setGravity(Gravity.CENTER);
        layout.addView(alive);

        TextView aliveDesc = new TextView(this);
        aliveDesc.setText(getString(R.string.about_alive_desc));
        aliveDesc.setTextSize(14);
        aliveDesc.setGravity(Gravity.CENTER);
        layout.addView(aliveDesc);

        Button aliveBtn = new Button(this);
        aliveBtn.setText(getString(R.string.about_visit_alive));
        aliveBtn.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://github.com/N3rdL0rd/alivecells"))));
        layout.addView(aliveBtn);

        setContentView(layout);
    }
}
