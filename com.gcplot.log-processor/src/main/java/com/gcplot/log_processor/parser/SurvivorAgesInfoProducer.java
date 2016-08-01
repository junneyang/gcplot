package com.gcplot.log_processor.parser;

import com.gcplot.log_processor.parser.survivor.AgesState;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.gcplot.commons.TroveUtils.copy;

/**
 * @author <a href="mailto:art.dm.ser@gmail.com">Artem Dmitriev</a>
 *         8/1/16
 */
public class SurvivorAgesInfoProducer {
    private static final String AGE_NUM_GROUP = "agenum";
    private static final String AGE_OCCUPIED_GROUP = "occupied";
    private static final String AGE_TOTAL_GROUP = "total";
    private static final String AGES_PREFIX = "- age";
    private static final Pattern AGE_PATTERN = Pattern.compile("- age[ \\t]+(?<" + AGE_NUM_GROUP + ">[0-9]+):[ \\t]+" +
            "(?<" + AGE_OCCUPIED_GROUP + ">[0-9]+)[ \\t]+bytes,[ \\t]+(?<" + AGE_TOTAL_GROUP + ">[0-9]+)[ \\t]+total");
    protected List<AgesState> agesStates = new ArrayList<>();
    protected TIntList occupied = new TIntArrayList();
    protected TIntList total = new TIntArrayList();
    protected int lastAge = -1;
    protected int maxAge = 0;
    protected Runnable onFinished = () -> {};

    public void parse(String s) {
        if (s.startsWith(AGES_PREFIX)) {
            Matcher m = AGE_PATTERN.matcher(s);
            if (m.matches()) {
                int age = Integer.parseInt(m.group(AGE_NUM_GROUP));
                int ocp = Integer.parseInt(m.group(AGE_OCCUPIED_GROUP));
                int ttl = Integer.parseInt(m.group(AGE_TOTAL_GROUP));
                if (age <= lastAge) {
                    finish();
                }
                occupied.add(ocp);
                total.add(ttl);
                lastAge = age;
            }
        }
    }

    public void finish() {
        onFinished.run();
        agesStates.add(new AgesState(copy(occupied), copy(total)));
        occupied.clear();
        total.clear();
        if (lastAge > maxAge) {
            maxAge = lastAge;
        }
    }

    public void reset() {
        agesStates.clear();
        occupied.clear();
        total.clear();
        lastAge = -1;
        maxAge = 0;
    }

    public AgesState averageAgesState() {
        if (agesStates.size() == 0) {
            return AgesState.NONE;
        }
        if (agesStates.size() == 1) {
            return agesStates.get(0);
        }
        int[] occupied = new int[maxAge];
        int[] total = new int[maxAge];
        int[] count = new int[maxAge];

        for (AgesState as : agesStates) {
            for (int i = 0; i < as.getOccupied().size(); i++) {
                occupied[i] += as.getOccupied().get(i);
                total[i] += as.getTotal().get(i);
                count[i]++;
            }
        }
        for (int i = 0; i < maxAge; i++) {
            occupied[i] /= count[i];
            total[i] /= count[i];
        }

        return new AgesState(new TIntArrayList(occupied), new TIntArrayList(total));
    }

    public void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

}
