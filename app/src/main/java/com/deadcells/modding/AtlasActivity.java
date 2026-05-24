package com.deadcells.modding;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AtlasActivity extends Activity {
    private TextView mStatus;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private void startOp(Runnable r) {
        new Thread(r).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);
        rootScroll.addView(layout);

        TextView title = new TextView(this);
        title.setText(getString(R.string.atlas_title));
        title.setTextSize(22);
        layout.addView(title);

        // --- Atlas unpack ---
        TextView ul = new TextView(this);
        ul.setText("\n" + getString(R.string.pak_atlas_unpack)); ul.setTextSize(16);
        layout.addView(ul);
        EditText ai = new EditText(this); ai.setHint(getString(R.string.pak_atlas_unpack_hint)); ai.setTextSize(14);
        layout.addView(ai);
        EditText ao = new EditText(this); ao.setHint(getString(R.string.pak_unpack_hint_dir)); ao.setTextSize(14);
        layout.addView(ao);
        Button ub = new Button(this); ub.setText(getString(R.string.pak_atlas_unpack_btn));
        ub.setOnClickListener(v -> {
            String i = ai.getText().toString().trim(), o = ao.getText().toString().trim();
            if (i.isEmpty() || o.isEmpty()) { Toast.makeText(this, getString(R.string.pak_fill_paths), Toast.LENGTH_SHORT).show(); return; }
            startOp(() -> {
                boolean ok = PakTool.atlasUnpack(i, o);
                mHandler.post(() -> mStatus.setText(ok ?
                    getString(R.string.pak_complete, o) : getString(R.string.pak_failed)));
            });
        });
        layout.addView(ub);

        // --- Atlas pack ---
        TextView pl = new TextView(this);
        pl.setText("\n" + getString(R.string.pak_atlas_pack)); pl.setTextSize(16);
        layout.addView(pl);
        EditText apd = new EditText(this); apd.setHint(getString(R.string.pak_atlas_pack_hint_dir)); apd.setTextSize(14);
        layout.addView(apd);
        EditText apa = new EditText(this); apa.setHint(getString(R.string.pak_atlas_pack_hint_atlas)); apa.setTextSize(14);
        layout.addView(apa);
        EditText app = new EditText(this); app.setHint(getString(R.string.pak_atlas_pack_hint_png)); app.setTextSize(14);
        layout.addView(app);
        Button pb = new Button(this); pb.setText(getString(R.string.pak_atlas_pack_btn));
        pb.setOnClickListener(v -> {
            String d = apd.getText().toString().trim(), a = apa.getText().toString().trim(), p = app.getText().toString().trim();
            if (d.isEmpty() || a.isEmpty() || p.isEmpty()) { Toast.makeText(this, getString(R.string.pak_fill_paths), Toast.LENGTH_SHORT).show(); return; }
            startOp(() -> {
                boolean ok = PakTool.atlasPack(d, a, p);
                mHandler.post(() -> mStatus.setText(ok ?
                    getString(R.string.pak_complete, a) : getString(R.string.pak_failed)));
            });
        });
        layout.addView(pb);

        mStatus = new TextView(this);
        mStatus.setTextSize(13);
        mStatus.setPadding(0, 16, 0, 0);
        layout.addView(mStatus);

        setContentView(rootScroll);
    }
}
