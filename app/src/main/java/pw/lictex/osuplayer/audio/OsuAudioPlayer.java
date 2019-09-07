package pw.lictex.osuplayer.audio;

import android.content.Context;
import lombok.Getter;
import lombok.Setter;
import lombok.var;
import pw.lictex.libosu.beatmap.HitObject;
import pw.lictex.libosu.beatmap.OsuBeatmap;
import pw.lictex.libosu.beatmap.SampleSet;
import pw.lictex.libosu.beatmap.TimingPoint;
import pw.lictex.osuplayer.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.un4seen.bass.BASS.BASS_SampleFree;

/**
 * Created by kpx on 2019/2/26.
 */
public class OsuAudioPlayer {
    private final AtomicInteger lastHitsoundTime = new AtomicInteger();
    @Getter private final AudioEngine engine;
    @Getter @Setter private int sampleOffset = 36;
    @Getter @Setter private int musicVolume = 100;
    @Getter @Setter private int soundVolume = 100;
    private volatile AudioEngine.Sample currentSpinnerSpinSound = null;
    private volatile AudioEngine.Sample currentSliderSlideSound = null;
    private volatile AudioEngine.Sample currentSliderWhistleSound = null;
    private Context context;
    private SampleManager sampleManager;
    private String currentBeatmapSetPath;
    private OsuBeatmap currentBeatmap = null;
    private List<HitObject> hitObjectsRemains = new ArrayList<>();

    public OsuAudioPlayer(Context context) {
        this.context = context;
        engine = new AudioEngine();
        sampleManager = new SampleManager();

        engine.setTickCallback(this::tick);
    }

    private void tick() {
        //no beatmap
        if (currentBeatmap == null) return;

        long currentTime = engine.getMainTrackCurrentTime() + sampleOffset;
        TimingPoint timingPoint = currentBeatmap.timingPoingAt((int) currentTime);

        SampleSet timingSampleSet = timingPoint != null ? timingPoint.getSampleSet() : SampleSet.Normal;
        int timingCustomSampleSet = timingPoint != null ? timingPoint.getCustomSampleSet() : 0;
        float timingVolume = (timingPoint != null ? timingPoint.getVolume() : 0) * soundVolume / 100f / 100f;

        boolean sliderSliding = false;
        boolean sliderWhistling = false;
        boolean spinnerSpinning = false;
        float spinnerCompletion = 0;
        SampleSet sliderSampleSet = SampleSet.Normal;
        SampleSet sliderAdditionSet = SampleSet.Normal;

        int lastHs = lastHitsoundTime.get();

        Iterator<HitObject> iterator = hitObjectsRemains.iterator();
        while (iterator.hasNext()) {
            HitObject hitObject = iterator.next();
            if (hitObject.getTime() > currentTime) break;

            int objectCustomSampleSet = (hitObject.getCustomSampleSetIndex() != 0) ? hitObject.getCustomSampleSetIndex() : timingCustomSampleSet;
            float objectVolume = (hitObject.getVolume() != 0) ? hitObject.getVolume() * soundVolume / 100f / 100f : timingVolume;

            if (hitObject instanceof HitObject.Slider) {
                HitObject.Slider slider = (HitObject.Slider) hitObject;

                sliderWhistling = (slider.getHitSounds() & 1 << 1) > 0;
                sliderSampleSet = (hitObject.getSampleSet() != SampleSet.None) ? hitObject.getSampleSet() : timingSampleSet;
                sliderAdditionSet = (hitObject.getAdditions() != SampleSet.None) ? hitObject.getAdditions() : sliderSampleSet;

                //region playSliderSample();
                double sliderDuration = currentBeatmap.getSliderDuration(slider);
                for (int i = 0; i < slider.getRepeat() + 1; i++) {
                    if (slider.getTime() + sliderDuration * i <= currentTime && slider.getTime() + sliderDuration * i > lastHitsoundTime.get()) {
                        SampleSet sampleSet = (slider.getEdgeSampleSet().get(i) != SampleSet.None) ? slider.getEdgeSampleSet().get(i) : timingSampleSet;
                        SampleSet additionSet = (slider.getEdgeAdditionSet().get(i) != SampleSet.None) ? slider.getEdgeAdditionSet().get(i) : sampleSet;

                        float pan = ((i % 2 == 0 ? slider.getX() : slider.getCurvePoints().get(slider.getCurvePoints().size() - 1)[0]) / 512f - 0.5f) * 0.8f;
                        int hitSounds = slider.getEdgeHitsounds().get(i);

                        //seems sliders are not affected by hitsounds override..?
                        sampleManager.getSample(sampleSet, "hitnormal", timingCustomSampleSet).play(timingVolume, pan);
                        if ((hitSounds & 1 << 1) > 0)
                            sampleManager.getSample(additionSet, "hitwhistle", timingCustomSampleSet).play(timingVolume, pan);
                        if ((hitSounds & 1 << 2) > 0)
                            sampleManager.getSample(additionSet, "hitfinish", timingCustomSampleSet).play(timingVolume, pan);
                        if ((hitSounds & 1 << 3) > 0)
                            sampleManager.getSample(additionSet, "hitclap", timingCustomSampleSet).play(timingVolume, pan);

                        lastHs = (int) Math.ceil(slider.getTime() + sliderDuration * i);
                    }
                }
                //endregion
                if (slider.getTime() + sliderDuration * slider.getRepeat() < currentTime) iterator.remove();
                else sliderSliding = true;
            }

            if (hitObject instanceof HitObject.Circle) {
                //region playCircleSample();
                if (lastHitsoundTime.get() <= hitObject.getTime()) {
                    SampleSet sampleSet = (hitObject.getSampleSet() != SampleSet.None) ? hitObject.getSampleSet() : timingSampleSet;
                    SampleSet additionSet = (hitObject.getAdditions() != SampleSet.None) ? hitObject.getAdditions() : sampleSet;

                    float pan = (hitObject.getX() / 512f - 0.5f) * 0.8f;
                    int hitSounds = hitObject.getHitSounds();

                    if (!hitObject.getSampleFile().isEmpty())
                        sampleManager.getSample(hitObject.getSampleFile().substring(0, hitObject.getSampleFile().lastIndexOf("."))).play(objectVolume, pan);
                    else
                        sampleManager.getSample(sampleSet, "hitnormal", objectCustomSampleSet).play(objectVolume, pan);

                    if ((hitSounds & 1 << 1) > 0)
                        sampleManager.getSample(additionSet, "hitwhistle", objectCustomSampleSet).play(objectVolume, pan);
                    if ((hitSounds & 1 << 2) > 0)
                        sampleManager.getSample(additionSet, "hitfinish", objectCustomSampleSet).play(objectVolume, pan);
                    if ((hitSounds & 1 << 3) > 0)
                        sampleManager.getSample(additionSet, "hitclap", objectCustomSampleSet).play(objectVolume, pan);
                    lastHs = (int) Math.ceil(hitObject.getTime());
                }

                //endregion
                iterator.remove();
            }

            if (hitObject instanceof HitObject.Spinner) {
                HitObject.Spinner spinner = (HitObject.Spinner) hitObject;
                if (spinner.getEndTime() < currentTime) {
                    SampleSet sampleSet = (spinner.getSampleSet() != SampleSet.None) ? spinner.getSampleSet() : timingSampleSet;
                    SampleSet additionSet = (spinner.getAdditions() != SampleSet.None) ? spinner.getAdditions() : sampleSet;

                    int hitSounds = spinner.getHitSounds();

                    if (!spinner.getSampleFile().isEmpty())
                        sampleManager.getSample(spinner.getSampleFile().substring(0, spinner.getSampleFile().lastIndexOf("."))).play(objectVolume, 0);
                    else
                        sampleManager.getSample(sampleSet, "hitnormal", objectCustomSampleSet).play(objectVolume, 0);

                    if ((hitSounds & 1 << 1) > 0)
                        sampleManager.getSample(additionSet, "hitwhistle", objectCustomSampleSet).play(objectVolume, 0);
                    if ((hitSounds & 1 << 2) > 0)
                        sampleManager.getSample(additionSet, "hitfinish", objectCustomSampleSet).play(objectVolume, 0);
                    if ((hitSounds & 1 << 3) > 0)
                        sampleManager.getSample(additionSet, "hitclap", objectCustomSampleSet).play(objectVolume, 0);
                    lastHs = (int) Math.ceil(spinner.getTime());

                    iterator.remove();
                } else {
                    spinnerSpinning = true;
                    spinnerCompletion = (float) (currentTime - spinner.getTime()) / (spinner.getEndTime() - spinner.getTime()) * 2;
                    if (spinnerCompletion >= 1) {
                        int x = (spinner.getEndTime() - spinner.getTime()) / 2;
                        lastHs = Math.max(lastHs, spinner.getTime() + x);
                        int t = 200;
                        while (currentTime - lastHs > t) {
                            sampleManager.getSample("spinnerbonus").play(timingVolume, 0);
                            lastHs += t;
                        }
                    }
                }
            }
        }

        lastHitsoundTime.set(lastHs);

        if (sliderSliding) {
            var slideSample = sampleManager.getSample(sliderSampleSet, "sliderslide", timingCustomSampleSet);
            if (currentSliderSlideSound != slideSample) {
                if (currentSliderSlideSound != null) currentSliderSlideSound.endLoop();
                currentSliderSlideSound = slideSample;
            }
            slideSample.loop(timingVolume);

            if (sliderWhistling) {
                var whistleSample = sampleManager.getSample(sliderAdditionSet, "sliderwhistle", timingCustomSampleSet);
                if (currentSliderWhistleSound != whistleSample) {
                    if (currentSliderWhistleSound != null) currentSliderWhistleSound.endLoop();
                    currentSliderWhistleSound = whistleSample;
                }
                whistleSample.loop(timingVolume);
            }
        } else {
            if (currentSliderSlideSound != null) {
                currentSliderSlideSound.endLoop();
                currentSliderSlideSound = null;
            }
            if (currentSliderWhistleSound != null) {
                currentSliderWhistleSound.endLoop();
                currentSliderWhistleSound = null;
            }
        }

        if (spinnerSpinning) {
            var spinnerSample = sampleManager.getSample("spinnerspin");
            if (currentSpinnerSpinSound != spinnerSample) {
                if (currentSpinnerSpinSound != null) currentSpinnerSpinSound.endLoop();
                currentSpinnerSpinSound = spinnerSample;
            }
            spinnerSample.loop(timingVolume, Math.min(100000, 20000 + (int) (40000 * spinnerCompletion)));
        } else {
            if (currentSpinnerSpinSound != null) {
                currentSpinnerSpinSound.endLoop();
                currentSpinnerSpinSound = null;
            }
        }
    }

    public void play() {
        engine.queueTaskSync(engine::resume);
    }

    public void pause() {
        engine.queueTaskSync(engine::pause);
    }

    public void setMusicVolume(int vol) {
        this.musicVolume = vol;
        engine.queueTaskSync(() -> engine.setMainTrackVolume(this.musicVolume / 100f));
    }

    public void setMod(Mod m) {
        engine.queueTaskSync(() -> {
            switch (m) {
                case DT:
                    engine.setTempo(1.5f);
                    engine.setPitch(1f);
                    break;
                case HT:
                    engine.setTempo(0.75f);
                    engine.setPitch(1f);
                    break;
                case NC:
                    engine.setTempo(1.5f);
                    engine.setPitch(1.5f);
                    break;
                case None:
                    engine.setTempo(1f);
                    engine.setPitch(1f);
                    break;
            }
        });
    }

    public void seekTo(long ms) {
        engine.queueTaskSync(() -> {
            engine.pause();
            engine.setTime(ms);
        });
        lastHitsoundTime.set((int) ms);
        hitObjectsRemains.clear();
        hitObjectsRemains.addAll(currentBeatmap.getHitObjectsSection().getHitObjects());
        engine.queueTaskSync(engine::resume);
    }

    public void openBeatmapSet(String dir) {
        engine.queueTaskSync(engine::stopMainTrack);
        currentBeatmap = null;
        sampleManager.setDirectory(dir);
        currentBeatmapSetPath = dir;
    }

    public void openBeatmap(String filename) {
        engine.queueTaskSync(engine::stopMainTrack);
        currentBeatmap = OsuBeatmap.fromFile(currentBeatmapSetPath + filename);
        engine.queueTaskSync(() -> {
            engine.playMainTrack(currentBeatmapSetPath + currentBeatmap.getGeneralSection().getAudioFilename());
            engine.setMainTrackVolume(this.musicVolume / 100f);
        });
        seekTo(0);
    }

    public long getAudioLength() {
        return engine.getMainTrackTotalTime();
    }

    public long getCurrentTime() {
        return engine.getMainTrackCurrentTime();
    }

    public enum Mod {
        None, DT, NC, HT
    }

    private class SampleManager {
        private Map<String, AudioEngine.Sample> defaultSet = new HashMap<>();
        private Map<String, AudioEngine.Sample> list = new HashMap<>();

        SampleManager() {
            addSample(defaultSet, "soft-hitnormal", context.getResources().openRawResource(R.raw.soft_hitnormal));
            addSample(defaultSet, "soft-hitclap", context.getResources().openRawResource(R.raw.soft_hitclap));
            addSample(defaultSet, "soft-hitfinish", context.getResources().openRawResource(R.raw.soft_hitfinish));
            addSample(defaultSet, "soft-hitwhistle", context.getResources().openRawResource(R.raw.soft_hitwhistle));
            addSample(defaultSet, "soft-sliderslide", context.getResources().openRawResource(R.raw.soft_sliderslide));
            addSample(defaultSet, "soft-slidertick", context.getResources().openRawResource(R.raw.soft_slidertick));
            addSample(defaultSet, "soft-sliderwhistle", context.getResources().openRawResource(R.raw.soft_sliderwhistle));

            addSample(defaultSet, "normal-hitnormal", context.getResources().openRawResource(R.raw.normal_hitnormal));
            addSample(defaultSet, "normal-hitclap", context.getResources().openRawResource(R.raw.normal_hitclap));
            addSample(defaultSet, "normal-hitfinish", context.getResources().openRawResource(R.raw.normal_hitfinish));
            addSample(defaultSet, "normal-hitwhistle", context.getResources().openRawResource(R.raw.normal_hitwhistle));
            addSample(defaultSet, "normal-sliderslide", context.getResources().openRawResource(R.raw.normal_sliderslide));
            addSample(defaultSet, "normal-slidertick", context.getResources().openRawResource(R.raw.normal_slidertick));
            addSample(defaultSet, "normal-sliderwhistle", context.getResources().openRawResource(R.raw.normal_sliderwhistle));

            addSample(defaultSet, "drum-hitnormal", context.getResources().openRawResource(R.raw.drum_hitnormal));
            addSample(defaultSet, "drum-hitclap", context.getResources().openRawResource(R.raw.drum_hitclap));
            addSample(defaultSet, "drum-hitfinish", context.getResources().openRawResource(R.raw.drum_hitfinish));
            addSample(defaultSet, "drum-hitwhistle", context.getResources().openRawResource(R.raw.drum_hitwhistle));
            addSample(defaultSet, "drum-sliderslide", context.getResources().openRawResource(R.raw.drum_sliderslide));
            addSample(defaultSet, "drum-slidertick", context.getResources().openRawResource(R.raw.drum_slidertick));
            addSample(defaultSet, "drum-sliderwhistle", context.getResources().openRawResource(R.raw.drum_sliderwhistle));

            addSample(defaultSet, "spinnerbonus", context.getResources().openRawResource(R.raw.spinnerbonus));
            addSample(defaultSet, "spinnerspin", context.getResources().openRawResource(R.raw.spinnerspin));
        }

        void addSample(Map<String, AudioEngine.Sample> m, String name, InputStream stream) {
            m.put(name, engine.createSample(name, stream));
        }

        void reset() {
            for (Map.Entry<String, AudioEngine.Sample> s : list.entrySet()) {
                BASS_SampleFree(s.getValue().ptr);
            }
            list.clear();
        }

        void setDirectory(String p) {
            reset();
            File f = new File(p);
            if (f.isDirectory()) {
                var files = f.listFiles(file -> {
                    if (!file.isFile()) return false;
                    if (file.getName().toLowerCase().endsWith(".wav")) return true;
                    if (file.getName().toLowerCase().endsWith(".ogg")) return true;

                    //mp3 samples is not supported
                    //if (file.getName().toLowerCase().endsWith(".mp3")) return true;
                    return false;
                });
                for (var file : files) {
                    try {
                        addSample(list, file.getName().substring(0, file.getName().lastIndexOf(".")), new FileInputStream(file));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        AudioEngine.Sample getSample(String name) {
            var sample = list.get(name);
            return (sample == null) ? getDefaultSample(name) : sample;
        }

        AudioEngine.Sample getDefaultSample(String name) {
            var sample = defaultSet.get(name.replaceAll("[0-9]+$", ""));
            return (sample == null) ? defaultSet.get("soft-hitnormal") : sample;
        }

        AudioEngine.Sample getSample(SampleSet sampleSet, String name, int custom) {
            if (custom == 0)
                return sampleManager.getDefaultSample(sampleSet.toString().toLowerCase() + "-" + name.toLowerCase());
            return sampleManager.getSample(sampleSet.toString().toLowerCase() + "-" + name.toLowerCase() + (custom > 1 ? custom : ""));
        }
    }
}