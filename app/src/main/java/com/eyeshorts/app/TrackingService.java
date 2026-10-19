package com.eyeshorts.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import com.eyeshorts.app.core.*;
import com.eyeshorts.app.RuntimeState.Mode;

public final class TrackingService extends LifecycleService {
    static final String CALIBRATE="com.eyeshorts.CALIBRATE", PRACTICE="com.eyeshorts.PRACTICE",
            TRACK="com.eyeshorts.TRACK", PAUSE="com.eyeshorts.PAUSE", STOP="com.eyeshorts.STOP",
            CHECK="com.eyeshorts.CHECK", TRIAL="com.eyeshorts.TRIAL",
            OBSERVE="com.eyeshorts.OBSERVE", SAVE_TRIAL="com.eyeshorts.SAVE_TRIAL",
            MOUTH_PRACTICE="com.eyeshorts.MOUTH_PRACTICE", MOUTH_SETTING="com.eyeshorts.MOUTH_SETTING";
    private static final String CHANNEL="tracking";
    private static final int NOTIFICATION=21;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Preferences preferences;
    private GazeCamera camera;
    private CalibrationSession calibration;
    private CommandDetector detector;
    private EvaluationSession evaluation;
    private InputMode inputMode;
    private Mode mode=Mode.OFF;
    private boolean cameraReady, destroyed, mouthPracticeOnly;
    private int generation, practiceCount, gestureCount, nextPracticeCount, previousPracticeCount, playbackPracticeCount;
    private String message="카메라 준비 중", finalMessage="추적을 종료했어요", eyeStatus="카메라 준비 중";
    private double progress;
    private long feedbackUntil;
    private String feedbackMessage="";
    private final BroadcastReceiver screenOff=new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish("화면이 꺼져 추적을 종료했어요"); }
    };
    static void command(Context context,String action) {
        Intent intent=new Intent(context,TrackingService.class).setAction(action);
        if (STOP.equals(action)) context.stopService(intent); else ContextCompat.startForegroundService(context,intent);
    }
    @Override public void onCreate() {
        super.onCreate(); preferences=new Preferences(this); inputMode=preferences.mode();
        message="카메라를 준비하고 있어요";
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(
                CHANNEL,getString(R.string.notification_channel),NotificationManager.IMPORTANCE_LOW));
        ContextCompat.registerReceiver(this,screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF),ContextCompat.RECEIVER_NOT_EXPORTED);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        super.onStartCommand(intent,flags,startId);
        if (intent==null) { stopSelf(); return START_NOT_STICKY; }
        String action=intent.getAction();
        if (STOP.equals(action)) { finish("추적을 종료했어요"); return START_NOT_STICKY; }
        try {
            if (Build.VERSION.SDK_INT>=30) startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
            else startForeground(NOTIFICATION,notification());
            if (SAVE_TRIAL.equals(action)) {
                if (mode==Mode.PRACTICE && evaluation!=null && evaluation.snapshot().kind()==EvaluationSession.Kind.TRIAL20) {
                    evaluation.finishTrial(); preferences.saveReport(inputMode,evaluation.snapshot());
                    message=evaluation.snapshot().summary(); publish();
                }
                return START_NOT_STICKY;
            }
            if (MOUTH_SETTING.equals(action)) {
                if ((mode==Mode.PRACTICE || mode==Mode.TRACKING || mode==Mode.OBSERVING) && !mouthPracticeOnly) {
                    if (!configureDetector()) return START_NOT_STICKY;
                    feedbackUntil=0;
                    message=preferences.mouthEnabled() ? "입 벌리기 제어를 켰어요" : "입 벌리기 제어를 껐어요";
                    publish(); updateNotification();
                } else if (mode==Mode.OFF) stopSelf();
                return START_NOT_STICKY;
            }
            if (PAUSE.equals(action)) {
                feedbackUntil=0;
                mode=Mode.PAUSED; if (detector!=null) detector.reset(); stopCamera();
                message="일시정지 · 카메라가 꺼졌어요";
            } else {
                if (ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
                    finish("카메라 권한을 허용해주세요"); return START_NOT_STICKY;
                }
                inputMode=preferences.mode(); evaluation=null; progress=0; feedbackUntil=0;
                mouthPracticeOnly=MOUTH_PRACTICE.equals(action);
                if (CALIBRATE.equals(action) && !inputMode.needsCalibration()) action=PRACTICE;
                if (CHECK.equals(action)) {
                    mode=Mode.CHECK; calibration=null; detector=null;
                    message="미리보기에서 얼굴과 양쪽 눈이 잘 보이는지 확인해주세요";
                } else if (CALIBRATE.equals(action)) {
                    mode=Mode.CALIBRATING; calibration=new CalibrationSession(inputMode); detector=null;
                    message=calibrationInstruction();
                } else if (TRACK.equals(action) || PRACTICE.equals(action) || MOUTH_PRACTICE.equals(action) || TRIAL.equals(action) || OBSERVE.equals(action)) {
                    boolean external=TRACK.equals(action) || OBSERVE.equals(action);
                    if (external && (!preferences.disclosed() || !ShortsAccessibilityService.available())) {
                        finish("EyeShorts 접근성 기능을 켜주세요"); return START_NOT_STICKY;
                    }
                    if (!configureDetector()) return START_NOT_STICKY;
                    calibration=null;
                    mode=OBSERVE.equals(action) ? Mode.OBSERVING : TRACK.equals(action) ? Mode.TRACKING : Mode.PRACTICE;
                    if (TRIAL.equals(action) || mouthPracticeOnly) practiceCount=nextPracticeCount=previousPracticeCount=playbackPracticeCount=0;
                    if (TRIAL.equals(action)) evaluation=new EvaluationSession(EvaluationSession.Kind.TRIAL20);
                    if (OBSERVE.equals(action)) evaluation=new EvaluationSession(EvaluationSession.Kind.PASSIVE5);
                    message=external ? "유튜브 쇼츠 또는 인스타그램 릴스를 열어주세요"
                            : mouthPracticeOnly ? "입을 약 0.3초 벌리면 재생/일시정지로 인식해요" : inputMode.instruction();
                } else { finish("앱에서 다시 시작해주세요"); return START_NOT_STICKY; }
                startCamera();
            }
            progress=0; publish(); updateNotification();
        } catch (RuntimeException e) { finish("카메라를 시작하지 못했어요. 앱을 연 상태에서 다시 시작해주세요."); }
        return START_NOT_STICKY;
    }
    private boolean configureDetector() {
        inputMode=preferences.mode();
        if (mouthPracticeOnly) {
            detector=new FaceCommandDetector(new MouthTrigger(),false);
            return true;
        }
        Calibration saved=preferences.calibration();
        if (inputMode.needsCalibration() && saved==null) { finish("선택한 방식으로 먼저 시선을 보정해주세요"); return false; }
        CommandDetector eyes=switch (inputMode) {
            case WINK -> new WinkTrigger();
            case RIGHT_RETURN -> new RightReturnTrigger(saved);
            case SWIPE_UP -> new EyeSwipeTrigger(saved);
            case DWELL -> new GazeTrigger(saved,preferences.dwellMs());
        };
        detector=new FaceCommandDetector(eyes,preferences.mouthEnabled());
        return true;
    }
    private void startCamera() {
        if (camera!=null) return;
        int token=++generation;
        camera=new GazeCamera(this,new GazeCamera.Listener() {
            @Override public void onReady() { main.post(() -> {
                if (destroyed || token!=generation) return; cameraReady=true; publish();
            }); }
            @Override public void onSample(GazeSample sample) { main.post(() -> {
                if (!destroyed && token==generation) handle(sample);
            }); }
            @Override public void onError(String error) { main.post(() -> {
                if (!destroyed && token==generation) finish(error);
            }); }
        });
        camera.start(this);
    }
    private String calibrationInstruction() {
        return switch (calibration.stage()) {
            case CENTER -> "머리는 그대로 두고 중앙 점을 바라봐주세요";
            case BOTTOM -> "머리는 그대로 두고 아래쪽 점을 바라봐주세요";
            case RIGHT -> "머리는 그대로 두고 오른쪽 점을 바라봐주세요";
            default -> "머리는 그대로 두고 위쪽 점을 바라봐주세요";
        };
    }
    private void handle(GazeSample sample) {
        eyeStatus=FaceQuality.from(sample).label();
        VideoCommand eyeDirection=!mouthPracticeOnly && inputMode==InputMode.WINK ? WinkTrigger.directionFor(sample) : null;
        if (eyeDirection!=null) eyeStatus+=" · "+(eyeDirection==VideoCommand.NEXT ? "오른쪽 → 다음" : "왼쪽 → 이전");
        if (mouthPracticeOnly) eyeStatus=MouthTrigger.stateLabel(sample);
        if (mode==Mode.CHECK) {
            if (preferences.mouthEnabled()) eyeStatus+="\n"+MouthTrigger.stateLabel(sample);
            publish(); return;
        }
        if (mode==Mode.CALIBRATING && calibration!=null) {
            calibration.update(sample); progress=calibration.progress();
            message=sample.usable() ? calibrationInstruction() : eyeStatus;
            if (calibration.stage()==CalibrationSession.Stage.FAILED) { finish(calibration.error()); return; }
            if (calibration.stage()==CalibrationSession.Stage.COMPLETE) {
                preferences.save(calibration.result());
                if (!configureDetector()) return;
                calibration=null; mode=Mode.PRACTICE; progress=0;
                message="보정 완료! "+inputMode.instruction(); updateNotification();
            }
            publish(); return;
        }
        boolean external=mode==Mode.TRACKING || mode==Mode.OBSERVING;
        if (detector==null || (!external && mode!=Mode.PRACTICE)) return;
        ShortsAccessibilityService.Target target=external ? ShortsAccessibilityService.target() : ShortsAccessibilityService.Target.OTHER_APP;
        boolean allowed=external ? target.playback() : RuntimeState.screenVisible;
        GazeTrigger.Update update=detector.update(sample,allowed);
        progress=update.progress();
        message=sample.timeMs()<feedbackUntil ? feedbackMessage : update.hint();
        if (external && !allowed) {
            feedbackUntil=0;
            message=switch (target) {
                case DISCONNECTED -> "접근성 연결이 끊겼어요. 설정에서 다시 켜주세요";
                case LOCKED -> "잠금 화면에서는 작동하지 않아요";
                case YOUTUBE_OTHER -> "쇼츠 재생 화면을 확인하지 못했어요. 쇼츠를 열어주세요";
                case INSTAGRAM_OTHER -> "릴스 재생 화면을 확인하지 못했어요. 릴스를 전체 화면으로 열어주세요";
                default -> "유튜브 쇼츠·인스타그램 릴스 화면에서 대기 중";
            };
        }
        if (evaluation!=null) {
            evaluation.update(sample.timeMs(),allowed,sample.faceUsable(),update.fire());
            if (mode==Mode.OBSERVING && evaluation.snapshot().finished()) {
                preferences.saveReport(inputMode,evaluation.snapshot()); message=evaluation.snapshot().summary();
                mode=Mode.PAUSED; stopCamera(); publish(); updateNotification(); return;
            }
        }
        if (update.fire()) {
            VideoCommand command=update.command();
            if (mode==Mode.PRACTICE) {
                practiceCount++;
                if (command==VideoCommand.NEXT) nextPracticeCount++;
                else if (command==VideoCommand.PREVIOUS) previousPracticeCount++;
                else playbackPracticeCount++;
                showFeedback(command==VideoCommand.PLAY_PAUSE ? "입 인식 성공 · 입을 닫아주세요"
                        : command.label()+" 인식 · "+(inputMode==InputMode.WINK ? "양쪽 눈을 떠주세요" : "중앙을 다시 바라봐주세요"));
            }
            else if (mode==Mode.OBSERVING) { showFeedback(command.label()+" 감지 · 관찰 중에는 실제로 넘기지 않아요"); updateNotification(); }
            else {
                int token=generation;
                ShortsAccessibilityService.execute(command,success -> {
                    if (destroyed || mode!=Mode.TRACKING || token!=generation) return;
                    if (success) gestureCount++;
                    showFeedback(success ? command.label()+(command.isTap() ? " 탭 완료" : " 제스처 완료")
                            : "명령을 보내지 못했어요. 쇼츠·릴스 화면과 접근성 설정을 확인해주세요");
                    publish(); updateNotification();
                });
            }
        }
        publish();
    }
    private void showFeedback(String text) {
        message=feedbackMessage=text; feedbackUntil=SystemClock.elapsedRealtime()+1200;
    }
    private void publish() {
        RuntimeState.live.setValue(new RuntimeState.Status(mode,cameraReady,message,progress,
                calibration==null ? "" : calibration.stage().name(),practiceCount,gestureCount,
                nextPracticeCount,previousPracticeCount,playbackPracticeCount,mouthPracticeOnly,
                eyeStatus,evaluation==null ? null : evaluation.snapshot()));
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,TrackingService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE);
        String text=mode==Mode.PAUSED ? "일시정지 · 카메라 꺼짐"
                : mode==Mode.OBSERVING ? "5분 관찰 · 실제 넘김 없음 · 감지 "+(evaluation==null ? 0 : evaluation.snapshot().detections())+"회"
                : mode==Mode.TRACKING ? inputMode.title()+" · 제스처 "+gestureCount+"회"
                : "미리보기 / 보정 / 연습 · 기기 안에서만 분석";
        NotificationCompat.Builder builder=new NotificationCompat.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(mode==Mode.PAUSED ? "EyeShorts · 일시정지" : getString(R.string.notification_title))
                .setContentText(text).setContentIntent(open).setOngoing(true).setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE).addAction(0,getString(R.string.stop),stop);
        if (mode==Mode.TRACKING || mode==Mode.OBSERVING) {
            PendingIntent pause=PendingIntent.getService(this,2,new Intent(this,TrackingService.class).setAction(PAUSE),PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(0,getString(R.string.pause),pause);
        }
        return builder.build();
    }
    private void updateNotification() { getSystemService(NotificationManager.class).notify(NOTIFICATION,notification()); }
    private void stopCamera() {
        generation++; cameraReady=false; eyeStatus="카메라 꺼짐";
        if (camera!=null) { camera.close(); camera=null; }
        PreviewBus.clear();
    }
    private void finish(String text) { finalMessage=text; stopSelf(); }
    @Override public void onTaskRemoved(Intent rootIntent) { finish("앱을 닫아 추적을 종료했어요"); super.onTaskRemoved(rootIntent); }
    @Override public void onDestroy() {
        destroyed=true; main.removeCallbacksAndMessages(null); stopCamera(); unregisterReceiver(screenOff);
        mode=Mode.OFF; progress=0; calibration=null; message=finalMessage; publish();
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
}
