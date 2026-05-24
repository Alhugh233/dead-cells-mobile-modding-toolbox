package io.github.libxposed.example;

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
                .setTitle("需要文件访问权限")
                .setMessage("PAK 工具需要「所有文件访问」权限。\n\n即将跳转到系统设置页面。")
                .setPositiveButton("去授权", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("取消", (d, w) -> finish())
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
            mStatus.setText("未授予文件访问权限");
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
        title.setText("PAK 工具");
        title.setTextSize(22);
        layout.addView(title);

        // --- Unpack ---
        TextView ul = new TextView(this);
        ul.setText("\n解包 PAK → 目录"); ul.setTextSize(16);
        layout.addView(ul);
        mPakPath = new EditText(this); mPakPath.setHint("PAK 文件路径"); mPakPath.setTextSize(14);
        layout.addView(mPakPath);
        mDirPath = new EditText(this); mDirPath.setHint("输出目录"); mDirPath.setTextSize(14);
        layout.addView(mDirPath);
        Button ub = new Button(this); ub.setText("解包");
        ub.setOnClickListener(v -> {
            String pak = mPakPath.getText().toString().trim();
            String dir = mDirPath.getText().toString().trim();
            if (pak.isEmpty() || dir.isEmpty()) { Toast.makeText(this, "请填写路径", Toast.LENGTH_SHORT).show(); return; }
            startOp(() -> {
                boolean ok = PakTool.unpack(pak, dir);
                mHandler.post(() -> mStatus.setText(ok ? ("解包完成: " + dir) : "解包失败"));
            });
        });
        layout.addView(ub);

        // --- Pack ---
        TextView pl = new TextView(this);
        pl.setText("\n打包 目录 → PAK"); pl.setTextSize(16);
        layout.addView(pl);
        EditText pd = new EditText(this); pd.setHint("源目录"); pd.setTextSize(14);
        layout.addView(pd);
        EditText po = new EditText(this); po.setHint("输出 PAK"); po.setTextSize(14);
        layout.addView(po);
        Button pb = new Button(this); pb.setText("打包");
        pb.setOnClickListener(v -> {
            String d = pd.getText().toString().trim(), o = po.getText().toString().trim();
            if (d.isEmpty() || o.isEmpty()) { Toast.makeText(this, "请填写路径", Toast.LENGTH_SHORT).show(); return; }
            startOp(() -> {
                boolean ok = PakTool.pack(d, o, null);
                mHandler.post(() -> mStatus.setText(ok ? ("打包完成: " + o) : "打包失败"));
            });
        });
        layout.addView(pb);

        // --- Merge ---
        TextView ml = new TextView(this);
        ml.setText("\n合并 PAK (逗号分隔多个输入)"); ml.setTextSize(16);
        layout.addView(ml);
        EditText mi = new EditText(this); mi.setHint("输入 PAK 路径, 用逗号分隔"); mi.setTextSize(14);
        layout.addView(mi);
        EditText mo = new EditText(this); mo.setHint("输出 PAK 路径"); mo.setTextSize(14);
        layout.addView(mo);
        Button mb = new Button(this); mb.setText("合并 PAK");
        mb.setOnClickListener(v -> {
            String[] ins = mi.getText().toString().split(",");
            String out = mo.getText().toString().trim();
            if (ins.length < 2 || out.isEmpty()) { Toast.makeText(this, "至少2个输入+输出", Toast.LENGTH_SHORT).show(); return; }
            String[] trimmed = new String[ins.length];
            for (int i = 0; i < ins.length; i++) trimmed[i] = ins[i].trim();
            startOp(() -> {
                boolean ok = PakTool.merge(out, null, trimmed);
                mHandler.post(() -> mStatus.setText(ok ? ("合并完成: " + out) : "合并失败"));
            });
        });
        layout.addView(mb);

        // --- Atlas unpack ---
        TextView al = new TextView(this);
        al.setText("\nAtlas 解包 → 精灵坐标"); al.setTextSize(16);
        layout.addView(al);
        EditText ai = new EditText(this); ai.setHint(".atlas 路径"); ai.setTextSize(14);
        layout.addView(ai);
        EditText ao = new EditText(this); ao.setHint("输出目录"); ao.setTextSize(14);
        layout.addView(ao);
        Button ab = new Button(this); ab.setText("解包 Atlas");
        ab.setOnClickListener(v -> {
            String i = ai.getText().toString().trim(), o = ao.getText().toString().trim();
            if (i.isEmpty() || o.isEmpty()) { Toast.makeText(this, "请填写路径", Toast.LENGTH_SHORT).show(); return; }
            startOp(() -> {
                boolean ok = PakTool.atlasUnpack(i, o);
                mHandler.post(() -> mStatus.setText(ok ? ("完成: " + o) : "失败"));
            });
        });
        layout.addView(ab);

        // --- Atlas pack ---
        TextView apl = new TextView(this);
        apl.setText("\nAtlas 打包 PNG → .atlas + .png"); apl.setTextSize(16);
        layout.addView(apl);
        EditText apd = new EditText(this); apd.setHint("PNG 目录"); apd.setTextSize(14);
        layout.addView(apd);
        EditText apa = new EditText(this); apa.setHint("输出 .atlas"); apa.setTextSize(14);
        layout.addView(apa);
        EditText app = new EditText(this); app.setHint("输出 .png"); app.setTextSize(14);
        layout.addView(app);
        Button apb = new Button(this); apb.setText("打包 Atlas");
        apb.setOnClickListener(v -> {
            String d = apd.getText().toString().trim(), a = apa.getText().toString().trim(), p = app.getText().toString().trim();
            if (d.isEmpty() || a.isEmpty() || p.isEmpty()) { Toast.makeText(this, "请填写所有路径", Toast.LENGTH_SHORT).show(); return; }
            startOp(() -> {
                boolean ok = PakTool.atlasPack(d, a, p);
                mHandler.post(() -> mStatus.setText(ok ? ("完成: " + a) : "失败"));
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
