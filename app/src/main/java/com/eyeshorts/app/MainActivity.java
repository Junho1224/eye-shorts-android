package com.eyeshorts.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.eyeshorts.app.core.TargetPolicy;
import com.eyeshorts.app.core.InputMode;
import com.eyeshorts.app.core.EvaluationSession;
import com.eyeshorts.app.RuntimeState.Mode;
import java.util.Locale;

public final class MainActivity extends ComponentActivity {
    private static final int BG = 0xFF101514, CARD = 0xFF1A2320, MINT = 0xFFAFF4CD;
    private static final int TEXT = 0xFFF1F5F2, MUTED = 0xFFA1B2A9;
    private Preferences preferences;
    private FrameLayout root, content;
    private TextView statusText, permissionText, calibrationText, countText, stageText, instructionText;
    private TextView eyeStatusText, comparisonText;
    private CameraPreviewView cameraPreview;
    private Button saveTrial;
    private Button startButton, stopButton, youtubeButton, instagramButton, practiceStart;
    private ProgressBar progress;
    private EyeTargetView targets;
    private boolean targetPage, notificationAsked;
    private String pendingAction;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { render(RuntimeState.live.getValue(), false); handler.postDelayed(this, 700); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = new Preferences(this);
        notificationAsked = state != null && state.getBoolean("notificationAsked");
        root = new FrameLayout(this); root.setBackgroundColor(BG);
        content = new FrameLayout(this); root.addView(content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        setContentView(root);
        RuntimeState.live.observe(this, status -> render(status, false));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (targetPage) TrackingService.command(MainActivity.this, TrackingService.STOP);
                else finish();
            }
        });
        render(RuntimeState.live.getValue(), true);
    }

    @Override protected void onResume() {
        super.onResume(); RuntimeState.screenVisible = true;
        handler.post(refresh);
    }
    @Override protected void onPause() {
        RuntimeState.screenVisible = false;
        if (cameraPreview!=null) PreviewBus.detach(cameraPreview);
        handler.removeCallbacks(refresh);
        super.onPause();
    }
    @Override protected void onStop() {
        RuntimeState.Status status = RuntimeState.live.getValue();
        if (!isChangingConfigurations() && status != null && (status.mode() == Mode.CALIBRATING || status.mode() == Mode.PRACTICE || status.mode()==Mode.CHECK))
            TrackingService.command(this, TrackingService.STOP);
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("notificationAsked", notificationAsked);
        super.onSaveInstanceState(state);
    }

    private void render(RuntimeState.Status state, boolean force) {
        if (state == null) return;
        boolean nextPage = state.mode() == Mode.CALIBRATING || state.mode() == Mode.PRACTICE;
        if (force || content.getChildCount() == 0 || nextPage != targetPage) {
            targetPage = nextPage;
            if (cameraPreview!=null) PreviewBus.detach(cameraPreview);
            content.removeAllViews();
            if (targetPage) buildTargetPage(); else buildHome();
        }
        if (RuntimeState.screenVisible && state.cameraReady()) PreviewBus.attach(cameraPreview);
        else PreviewBus.detach(cameraPreview);
        eyeStatusText.setText(state.eyeStatus());
        if (targetPage) {
            boolean practicing = state.mode() == Mode.PRACTICE;
            boolean top = "TOP".equals(state.calibrationStage());
            boolean bottom = "BOTTOM".equals(state.calibrationStage());
            InputMode input=preferences.mode();
            boolean right="RIGHT".equals(state.calibrationStage());
            targets.update(top,bottom,right,practicing,input,state.progress(),state.mouthPracticeOnly());
            stageText.setText(practicing ? (state.mouthPracticeOnly() ? "입 벌리기" : input.title())+" · 연습"
                    : right ? "오른쪽 점 보정  2 / 2" : top ? "위쪽 점 보정  2 / 2" : bottom ? "아래쪽 점 보정  1 / 2" : "중앙 점 보정  1 / 2");
            instructionText.setText(state.message());
            boolean trial=state.evaluation()!=null && state.evaluation().kind()==EvaluationSession.Kind.TRIAL20;
            String counts=getString(R.string.practice_command_counts,state.nextPracticeCount(),state.previousPracticeCount(),state.playbackPracticeCount());
            if (!practicing) countText.setText(R.string.calibration_position_tip);
            else if (state.mouthPracticeOnly()) countText.setText(getString(R.string.mouth_practice_count,state.playbackPracticeCount()));
            else if (trial) countText.setText(getString(R.string.trial_practice_count,state.evaluation().detections(),counts));
            else countText.setText(getString(R.string.practice_count_summary,counts));
            // Reserve the same space so calibration and practice dots stay in identical positions.
            practiceStart.setVisibility(practicing ? View.VISIBLE : View.INVISIBLE);
            saveTrial.setVisibility(practicing && trial ? View.VISIBLE : View.INVISIBLE);
            saveTrial.setEnabled(trial && !state.evaluation().finished());
            return;
        }
        boolean calibrated = preferences.ready();
        boolean connected = preferences.disclosed() && ShortsAccessibilityService.available();
        permissionText.setText(connected ? "연결됨 · 유튜브 쇼츠·인스타그램 릴스" : "쇼츠·릴스에 다음/이전 넘기기 동작을 보내요");
        calibrationText.setText(!preferences.mode().needsCalibration() ? "윙크는 시선 위치 보정 없이 연습할 수 있어요"
                : calibrated ? "보정 완료 · 자세가 바뀌면 다시 해주세요" : calibrationDescription());
        String detail=state.message();
        if (state.evaluation()!=null && state.evaluation().kind()==EvaluationSession.Kind.PASSIVE5) {
            long remaining=Math.max(0,300_000-state.evaluation().elapsedMs());
            detail+="\n관찰 남은 시간 "+String.format(Locale.KOREA,"%d:%02d",remaining/60000,(remaining/1000)%60)
                    +" · 감지 "+state.evaluation().detections()+"회 · 실제 넘김 없음";
        }
        statusText.setText(detail);
        progress.setProgress((int)(state.progress() * 100));
        boolean tracking = state.mode() == Mode.TRACKING || state.mode()==Mode.OBSERVING;
        startButton.setText(tracking ? "일시정지" : state.mode() == Mode.PAUSED ? "추적 다시 시작"
                : !calibrated ? "시선 보정부터 시작하기" : !connected ? "쇼츠·릴스 제어 연결하기" : "눈으로 넘기기 시작");
        stopButton.setVisibility(state.running() ? View.VISIBLE : View.GONE);
        youtubeButton.setEnabled(tracking && state.cameraReady());
        youtubeButton.setAlpha(youtubeButton.isEnabled() ? 1 : 0.45f);
        instagramButton.setEnabled(tracking && state.cameraReady());
        instagramButton.setAlpha(instagramButton.isEnabled() ? 1 : 0.45f);
        StringBuilder reports=new StringBuilder();
        for (InputMode input:InputMode.values()) reports.append(input.title()).append("\n").append(preferences.report(input)).append("\n\n");
        if (!comparisonText.getText().toString().equals(reports.toString())) comparisonText.setText(reports.toString());
    }

    private void buildHome() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout column = vertical(); column.setPadding(dp(24), dp(24), dp(24), dp(28));
        scroll.addView(column); content.addView(scroll);
        TextView brand = text("◉  EyeShorts", 20, MINT); brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        column.addView(brand);
        addSpace(column, 16);
        TextView title = text("눈으로 쇼츠·릴스.", 30, TEXT); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLineSpacing(0, 1.02f); column.addView(title);
        addSpace(column, 12);
        column.addView(text(preferences.mode().instruction(), 15, MUTED));
        addSpace(column, 24);

        LinearLayout statusCard = card();
        statusCard.addView(text("LIVE  ·  현재 상태", 12, MINT));
        statusText = text("", 16, TEXT); statusText.setPadding(0, dp(12), 0, dp(14)); statusCard.addView(statusText);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));
        statusCard.addView(progress, new LinearLayout.LayoutParams(-1, dp(5)));
        column.addView(statusCard); addSpace(column, 14);

        LinearLayout previewCard=card(); previewCard.addView(text("카메라 · 눈 인식 확인",17,TEXT));
        cameraPreview=new CameraPreviewView(this); previewCard.addView(cameraPreview,new LinearLayout.LayoutParams(-1,dp(160)));
        eyeStatusText=text("",13,MINT); previewCard.addView(eyeStatusText);
        previewCard.addView(button("카메라 미리보기 켜기",false,() -> requestStart(TrackingService.CHECK)));
        column.addView(previewCard); addSpace(column,12);

        LinearLayout modeCard = card();
        modeCard.addView(text("넘기는 방법", 17, TEXT));
        for (InputMode input:InputMode.values()) {
            boolean selected=preferences.mode()==input;
            modeCard.addView(button((selected ? "✓  " : "")+input.title(),selected,() -> selectMode(input)));
        }
        column.addView(modeCard); addSpace(column, 12);

        LinearLayout mouthCard=card(); mouthCard.addView(text("입으로 재생·일시정지",17,TEXT));
        mouthCard.addView(text("입을 약 0.3초 벌리면 영상을 한 번 탭해요.\n입을 닫았다 다시 벌리면 또 한 번 동작해요.",13,MUTED));
        CheckBox mouthSwitch=new CheckBox(this); mouthSwitch.setText("입 벌리기 제어 사용");
        mouthSwitch.setTextColor(TEXT); mouthSwitch.setTextSize(15); mouthSwitch.setMinHeight(dp(48));
        mouthSwitch.setButtonTintList(android.content.res.ColorStateList.valueOf(MINT));
        mouthSwitch.setChecked(preferences.mouthEnabled());
        mouthSwitch.setOnCheckedChangeListener((button,checked) -> {
            preferences.mouthEnabled(checked);
            RuntimeState.Status current=RuntimeState.live.getValue();
            if (current!=null && (current.mode()==Mode.PRACTICE || current.mode()==Mode.TRACKING || current.mode()==Mode.OBSERVING))
                TrackingService.command(this,TrackingService.MOUTH_SETTING);
        });
        mouthCard.addView(mouthSwitch);
        mouthCard.addView(button("입 벌리기 연습",false,() -> requestStart(TrackingService.MOUTH_PRACTICE)));
        column.addView(mouthCard); addSpace(column,12);

        LinearLayout calibrationCard = card();
        calibrationCard.addView(text("01   내 눈에 맞추기", 17, TEXT));
        calibrationText = text("", 13, MUTED); calibrationText.setPadding(0, dp(8), 0, dp(8)); calibrationCard.addView(calibrationText);
        calibrationCard.addView(button(preferences.mode().needsCalibration() ? "시선 보정하기" : "윙크 연습하기", false, () -> requestStart(TrackingService.CALIBRATE)));
        column.addView(calibrationCard); addSpace(column, 12);

        LinearLayout permissionCard = card();
        permissionCard.addView(text("02   쇼츠·릴스 제어 연결", 17, TEXT));
        permissionText = text("", 13, MUTED); permissionText.setPadding(0, dp(8), 0, dp(8)); permissionCard.addView(permissionText);
        permissionCard.addView(button("접근성 연결 설정", false, this::accessibility));
        column.addView(permissionCard); addSpace(column, 20);

        TextView duration = text(durationLabel(preferences.dwellMs()), 15, TEXT);
        duration.setVisibility(preferences.mode()==InputMode.DWELL ? View.VISIBLE : View.GONE); column.addView(duration);
        SeekBar seek = new SeekBar(this); seek.setMax(12); seek.setProgress((int)((preferences.dwellMs()-600)/100));
        seek.setContentDescription("넘기기까지 위쪽을 바라볼 시간. 0.6초에서 1.8초");
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(MINT));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { duration.setText(durationLabel(600L + value*100L)); }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                preferences.dwellMs(600L + bar.getProgress()*100L);
                RuntimeState.Status state = RuntimeState.live.getValue();
                if (state != null && state.mode() == Mode.TRACKING) requestStart(TrackingService.TRACK);
            }
        });
        column.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));
        seek.setVisibility(preferences.mode()==InputMode.DWELL ? View.VISIBLE : View.GONE);
        addSpace(column, 8);
        startButton = button("", true, () -> {
            RuntimeState.Status state = RuntimeState.live.getValue();
            if (state != null && (state.mode() == Mode.TRACKING || state.mode()==Mode.OBSERVING)) TrackingService.command(this, TrackingService.PAUSE);
            else if (!preferences.ready()) requestStart(TrackingService.CALIBRATE);
            else requestStart(TrackingService.TRACK);
        });
        column.addView(startButton); addSpace(column, 10);
        youtubeButton = button("유튜브 열기 ↗", false, () -> openVideoApp(TargetPolicy.YOUTUBE)); column.addView(youtubeButton);
        instagramButton = button("인스타그램 열기 ↗", false, () -> openVideoApp(TargetPolicy.INSTAGRAM)); column.addView(instagramButton);
        addSpace(column, 10);
        column.addView(button("앱 안에서 눈동작 연습", false, () -> requestStart(preferences.ready() ? TrackingService.PRACTICE : TrackingService.CALIBRATE)));
        stopButton = button("추적 종료 · 카메라 끄기", false, () -> TrackingService.command(this, TrackingService.STOP));
        column.addView(stopButton);
        addSpace(column,16);
        LinearLayout comparison=card(); comparison.addView(text("방식 비교 테스트",17,TEXT));
        comparison.addView(text("눈동작을 비교할 때는 입 제어를 끄고 20회 시도해주세요.\n5분 관찰은 명령만 기록하고 영상을 제어하지 않아요.",13,MUTED));
        comparison.addView(button("20회 입력 테스트",false,() -> requestStart(TrackingService.TRIAL)));
        comparison.addView(button("쇼츠·릴스에서 5분 오작동 관찰",false,() -> requestStart(TrackingService.OBSERVE)));
        comparisonText=text("",12,MUTED); comparison.addView(comparisonText); column.addView(comparison);
        addSpace(column, 24);
        TextView privacy = text("눈·입 영상은 휴대폰 안에서만 분석해요.\n녹화·저장·외부 전송하지 않아요.\n\nv0.1.6 · 실험용 버전", 12, MUTED);
        privacy.setGravity(Gravity.CENTER); column.addView(privacy);
    }

    private void buildTargetPage() {
        LinearLayout page = vertical(); content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout targetArea = new FrameLayout(this); page.addView(targetArea, new LinearLayout.LayoutParams(-1, 0, 1));
        targets = new EyeTargetView(this); targetArea.addView(targets, new FrameLayout.LayoutParams(-1, -1));
        cameraPreview=new CameraPreviewView(this);
        FrameLayout.LayoutParams previewParams=new FrameLayout.LayoutParams(dp(96),dp(128),Gravity.TOP|Gravity.START);
        previewParams.setMargins(dp(6),dp(6),0,0); targetArea.addView(cameraPreview,previewParams);
        stageText = text("", 16, MUTED); stageText.setGravity(Gravity.CENTER);
        page.addView(stageText, 0, new LinearLayout.LayoutParams(-1, dp(40)));
        LinearLayout instructions = vertical(); instructions.setPadding(dp(24), dp(6), dp(24), dp(8));
        instructions.setBackgroundColor(BG);
        eyeStatusText=text("",12,MINT); eyeStatusText.setGravity(Gravity.CENTER); eyeStatusText.setMinHeight(dp(30)); instructions.addView(eyeStatusText);
        instructionText = text("", 17, TEXT); instructionText.setGravity(Gravity.CENTER); instructionText.setMinHeight(dp(52));
        instructions.addView(instructionText); addSpace(instructions, 8);
        countText = text("", 13, MUTED); countText.setGravity(Gravity.CENTER); countText.setMinHeight(dp(42)); instructions.addView(countText);
        addSpace(instructions, 4);
        practiceStart = button("쇼츠·릴스에서 사용하기", true, () -> requestStart(TrackingService.TRACK)); instructions.addView(practiceStart);
        saveTrial=button("20회 시도 완료 · 결과 저장",false,() -> requestStart(TrackingService.SAVE_TRIAL)); instructions.addView(saveTrial);
        instructions.addView(button("닫기 · 카메라 끄기", false, () -> TrackingService.command(this, TrackingService.STOP)));
        page.addView(instructions, new LinearLayout.LayoutParams(-1, -2));
    }

    private String calibrationDescription() {
        return switch (preferences.mode()) {
            case RIGHT_RETURN -> "중앙과 오른쪽 점을 보며 약 6초간 맞춰요";
            case SWIPE_UP -> "아래쪽과 위쪽 점을 보며 약 6초간 맞춰요";
            case DWELL -> "중앙과 위쪽 점을 보며 약 6초간 맞춰요";
            default -> "보정 없이 오른쪽 눈은 다음, 왼쪽 눈은 이전으로 넘겨요";
        };
    }

    private void selectMode(InputMode mode) {
        if (preferences.mode() == mode) return;
        TrackingService.command(this, TrackingService.STOP);
        preferences.mode(mode);
        render(RuntimeState.live.getValue(), true);
    }

    private void accessibility() {
        if (preferences.disclosed()) { openAccessibilitySettings(); return; }
        new AlertDialog.Builder(this).setTitle("쇼츠·릴스 제어 권한 안내")
                .setMessage("EyeShorts는 접근성 기능으로 유튜브와 인스타그램의 앱 이름, 화면 요소 ID와 위치를 읽어 쇼츠 또는 릴스 재생 화면인지 확인합니다. 사용자가 눈으로 명령하면 다음 영상은 위로, 이전 영상은 아래로 스와이프합니다. 윙크 모드에서 오른쪽 눈은 다음, 왼쪽 눈은 이전입니다. 입 벌리기 제어를 켜면 입을 벌릴 때 영상 중앙을 한 번 탭해 재생/일시정지를 전환합니다.\n\n화면의 글이나 영상은 수집하지 않으며, 확인한 화면 구조도 저장하거나 외부에 전송하지 않습니다. 카메라의 눈·입 영상은 기기 안에서만 분석합니다.\n\n이 권한은 선택 사항입니다. 연결하지 않아도 앱 안에서 눈·입 동작 연습을 할 수 있어요. Android 접근성 설정에서 언제든 해제할 수 있습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("동의하고 설정 열기", (dialog, which) -> { preferences.acceptDisclosure(); openAccessibilitySettings(); }).show();
    }
    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        Toast.makeText(this, "설치된 앱에서 EyeShorts를 찾아 켜주세요", Toast.LENGTH_LONG).show();
    }

    private void requestStart(String action) {
        if ((TrackingService.TRACK.equals(action) || TrackingService.OBSERVE.equals(action))
                && (!preferences.disclosed() || !ShortsAccessibilityService.available())) { accessibility(); return; }
        if ((TrackingService.TRACK.equals(action) || TrackingService.PRACTICE.equals(action)
                || TrackingService.TRIAL.equals(action) || TrackingService.OBSERVE.equals(action)) && !preferences.ready()) {
            Toast.makeText(this,"먼저 보정한 뒤 원하는 기능을 다시 눌러주세요",Toast.LENGTH_LONG).show();
            action=TrackingService.CALIBRATE;
        }
        pendingAction = action;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 10); return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !notificationAsked
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationAsked = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11); return;
        }
        try { TrackingService.command(this, action); }
        catch (RuntimeException e) { new AlertDialog.Builder(this).setMessage("시작하지 못했어요. 카메라 권한을 확인하고 앱을 연 상태에서 다시 시도해주세요.").setPositiveButton("확인", null).show(); }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 10 && (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)) {
            new AlertDialog.Builder(this).setTitle("카메라 권한이 필요해요")
                    .setMessage("전면 카메라로 눈동작을 분석합니다. 권한을 다시 물어보지 않는 경우 Android 앱 설정에서 카메라를 허용해주세요.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        if ((code == 10 || code == 11) && pendingAction != null) requestStart(pendingAction);
    }
    private void openVideoApp(String packageName) {
        boolean youtube=TargetPolicy.YOUTUBE.equals(packageName);
        String name=youtube ? "유튜브" : "인스타그램";
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) { Toast.makeText(this, name+" 앱을 먼저 설치해주세요", Toast.LENGTH_LONG).show(); return; }
        startActivity(intent);
        Toast.makeText(this,(youtube ? "쇼츠" : "릴스")+"를 전체 화면으로 열어주세요. "+(preferences.mode()==InputMode.WINK ? "오른쪽 눈은 다음 · 왼쪽 눈은 이전" : "중앙을 먼저 바라봐주세요")
                +(preferences.mouthEnabled() ? " · 입 벌리기는 재생/일시정지" : ""),Toast.LENGTH_LONG).show();
    }

    private String durationLabel(long millis) { return String.format(Locale.KOREA, "넘기기까지 바라볼 시간  ·  %.1f초", millis/1000.0); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout vertical() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(3), 1); return view;
    }
    private LinearLayout card() {
        LinearLayout card = vertical(); card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(background(CARD, 20)); return card;
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private Button button(String label, boolean primary, Runnable onClick) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(15);
        button.setTextColor(primary ? BG : TEXT); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(background(primary ? MINT : 0xFF293B32, 15));
        button.setMinHeight(dp(52)); button.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(4); params.bottomMargin = dp(4); button.setLayoutParams(params);
        button.setOnClickListener(view -> onClick.run()); return button;
    }
    private void addSpace(LinearLayout parent, int size) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size))); }
}
