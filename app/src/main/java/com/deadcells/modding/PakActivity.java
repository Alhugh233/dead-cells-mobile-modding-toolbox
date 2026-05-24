package com.deadcells.modding;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class PakActivity extends Activity {
    private EditText mPakPath, mDirPath;
    private TextView mStatus;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.pak_need_permission_title))
                .setMessage(getString(R.string.pak_need_permission_msg))
                .setPositiveButton(getString(R.string.pak_grant), (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton(getString(R.string.cancel), (d, w) -> finish())
                .setCancelable(false)
                .show();
        }
    }

    private void startOp(Runnable r) {
        new Thread(r).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasStoragePermission()) {
            mStatus.setText(getString(R.string.pak_no_permission));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!hasStoragePermission()) {
            requestStoragePermission();
        }

        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);
        rootScroll.addView(layout);

        TextView title = new TextView(this);
        title.setText(getString(R.string.pak_title));
        title.setTextSize(22);
        layout.addView(title);

        // --- Unpack ---
        TextView ul = new TextView(this);
        ul.setText("\n" + getString(R.string.pak_unpack)); ul.setTextSize(16);
        layout.addView(ul);
        mPakPath = new EditText(this); mPakPath.setHint(getString(R.string.pak_unpack_hint_pak)); mPakPath.setTextSize(14);
        layout.addView(mPakPath);
        mDirPath = new EditText(this); mDirPath.setHint(getString(R.string.pak_unpack_hint_dir)); mDirPath.setTextSize(14);
        layout.addView(mDirPath);
        Button ub = new Button(this); ub.setText(getString(R.string.pak_unpack_btn));
        ub.setOnClickListener(v -> {
            String pak = mPakPath.getText().toString().trim();
            String dir = mDirPath.getText().toString().trim();
            if (pak.isEmpty() || dir.isEmpty()) { Toast.makeText(this, getString(R.string.pak_fill_paths), Toast.LENGTH_SHORT).show(); return; }
            startOp(() -> {
                boolean ok = PakTool.unpack(pak, dir);
                mHandler.post(() -> mStatus.setText(ok ?
                    getString(R.string.pak_complete, dir) : getString(R.string.pak_failed)));
            });
        });
        layout.addView(ub);

        // --- Pack ---
        TextView pl = new TextView(this);
        pl.setText("\n" + getString(R.string.pak_pack)); pl.setTextSize(16);
        layout.addView(pl);
        EditText pd = new EditText(this); pd.setHint(getString(R.string.pak_pack_hint_dir)); pd.setTextSize(14);
        layout.addView(pd);
        EditText po = new EditText(this); po.setHint(getString(R.string.pak_pack_hint_pak)); po.setTextSize(14);
        layout.addView(po);
        Button pb = new Button(this); pb.setText(getString(R.string.pak_pack_btn));
        pb.setOnClickListener(v -> {
            String d = pd.getText().toString().trim(), o = po.getText().toString().trim();
            if (d.isEmpty() || o.isEmpty()) { Toast.makeText(this, getString(R.string.pak_fill_paths), Toast.LENGTH_SHORT).show(); return; }
            startOp(() -> {
                boolean ok = PakTool.pack(d, o, null);
                mHandler.post(() -> mStatus.setText(ok ?
                    getString(R.string.pak_complete, o) : getString(R.string.pak_failed)));
            });
        });
        layout.addView(pb);

        // --- Merge ---
        TextView ml = new TextView(this);
        ml.setText("\n" + getString(R.string.pak_merge)); ml.setTextSize(16);
        layout.addView(ml);
        EditText mi = new EditText(this); mi.setHint(getString(R.string.pak_merge_hint_in)); mi.setTextSize(14);
        layout.addView(mi);
        EditText mo = new EditText(this); mo.setHint(getString(R.string.pak_merge_hint_out)); mo.setTextSize(14);
        layout.addView(mo);
        Button mb = new Button(this); mb.setText(getString(R.string.pak_merge_btn));
        mb.setOnClickListener(v -> {
            String[] ins = mi.getText().toString().split(",");
            String out = mo.getText().toString().trim();
            if (ins.length < 2 || out.isEmpty()) { Toast.makeText(this, getString(R.string.pak_fill_paths), Toast.LENGTH_SHORT).show(); return; }
            String[] trimmed = new String[ins.length];
            for (int i = 0; i < ins.length; i++) trimmed[i] = ins[i].trim();
            startOp(() -> {
                boolean ok = PakTool.merge(out, null, trimmed);
                mHandler.post(() -> mStatus.setText(ok ?
                    getString(R.string.pak_complete, out) : getString(R.string.pak_failed)));
            });
        });
        layout.addView(mb);

        // --- Atlas unpack ---
        TextView al = new TextView(this);
        al.setText("\n" + getString(R.string.pak_atlas_unpack)); al.setTextSize(16);
        layout.addView(al);
        EditText ai = new EditText(this); ai.setHint(getString(R.string.pak_atlas_unpack_hint)); ai.setTextSize(14);
        layout.addView(ai);
        EditText ao = new EditText(this); ao.setHint(getString(R.string.pak_unpack_hint_dir)); ao.setTextSize(14);
        layout.addView(ao);
        Button ab = new Button(this); ab.setText(getString(R.string.pak_atlas_unpack_btn));
        ab.setOnClickListener(v -> {
            String i = ai.getText().toString().trim(), o = ao.getText().toString().trim();
            if (i.isEmpty() || o.isEmpty()) { Toast.makeText(this, getString(R.string.pak_fill_paths), Toast.LENGTH_SHORT).show(); return; }
            startOp(() -> {
                boolean ok = PakTool.atlasUnpack(i, o);
                mHandler.post(() -> mStatus.setText(ok ?
                    getString(R.string.pak_complete, o) : getString(R.string.pak_failed)));
            });
        });
        layout.addView(ab);

        // --- Atlas pack ---
        TextView apl = new TextView(this);
        apl.setText("\n" + getString(R.string.pak_atlas_pack)); apl.setTextSize(16);
        layout.addView(apl);
        EditText apd = new EditText(this); apd.setHint(getString(R.string.pak_atlas_pack_hint_dir)); apd.setTextSize(14);
        layout.addView(apd);
        EditText apa = new EditText(this); apa.setHint(getString(R.string.pak_atlas_pack_hint_atlas)); apa.setTextSize(14);
        layout.addView(apa);
        EditText app = new EditText(this); app.setHint(getString(R.string.pak_atlas_pack_hint_png)); app.setTextSize(14);
        layout.addView(app);
        Button apb = new Button(this); apb.setText(getString(R.string.pak_atlas_pack_btn));
        apb.setOnClickListener(v -> {
            String d = apd.getText().toString().trim(), a = apa.getText().toString().trim(), p = app.getText().toString().trim();
            if (d.isEmpty() || a.isEmpty() || p.isEmpty()) { Toast.makeText(this, getString(R.string.pak_fill_paths), Toast.LENGTH_SHORT).show(); return; }
            startOp(() -> {
                boolean ok = PakTool.atlasPack(d, a, p);
                mHandler.post(() -> mStatus.setText(ok ?
                    getString(R.string.pak_complete, a) : getString(R.string.pak_failed)));
            });
        });
        layout.addView(apb);

        mStatus = new TextView(this);
        mStatus.setTextSize(13);
        mStatus.setPadding(0, 16, 0, 0);
        layout.addView(mStatus);

        setContentView(rootScroll);
    }
}
