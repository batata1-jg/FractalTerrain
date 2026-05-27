package me.batata_1.fractal_terrain.hydrology;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

public class PipelinePreprocessing {

    private static final Queue<Integer> isLeaf = new ArrayDeque<>();
    private static final int[] dx = {-1, 1, -1, 1, 0, 0, -1, 1};
    private static final int[] dz = {1, 1, -1, -1, -1, 1, 0, 0};

    public static int neighbor(float adj) {
        for (int i = 0; i < 8; i++) if ((((int) adj) & (1 << i))!=0) return i;
        return -1;
    }

    public static float[] computeFlow(final float[] adj, final int size) {
        final int[] inDegree = new int[size * size];
        final float[] flow = new float[size * size];
        Arrays.fill(inDegree, 0);
        Arrays.fill(flow, 1);
        isLeaf.clear();
        for (int id = 0; id < size * size; id++) {
            final int nId = neighbor(adj[id]);
            if (nId == -1) continue;
            inDegree[id + size * dx[nId] + dz[nId]]++;
        }
        for (int id = 0; id < size * size; id++) if (inDegree[id] == 0) isLeaf.add(id);
        while (!isLeaf.isEmpty()) {
            final int cur = isLeaf.poll();
            final int nId = neighbor(adj[cur]);
            if (nId == -1) continue;
            final int viz = cur + size * dx[nId] + dz[nId];
            if ((--inDegree[viz]) == 0) isLeaf.add(viz);
            flow[viz] += flow[cur];
        }
        return flow;
    }

    public static float[] computeDirection(final float[] elev, final float[] ww,final int size) {
        final float[] adj = new float[size * size];
        float curSlope;
        int curDirection;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                final int id = x * size + z;
                final float idElev = (ww[id] > 1e-6f) ? (elev[id] / ww[id]) : 0f;
                curSlope = 0;
                curDirection = 0;
                for (int i = 0; i < 8; i++) {
                    if ((x == 0 && dx[i] == -1) || (z == 0 && dz[i] == -1)) continue;
                    if ((x == (size - 1) && dx[i] == 1) || (z == (size - 1) && dz[i] == 1)) continue;
                    final int did = size * (x + dx[i]) + z + dz[i];
                    final float visElev = (ww[did] > 1e-6f) ? (elev[did] / ww[did]) : 0f;
                    final float visSlope = (visElev - idElev) / ((i<4)?1.4142f:1f);
                    if (visSlope>=curSlope) continue;
                    curSlope = visSlope;
                    curDirection = 1 << i;
                }
                adj[id] = curDirection;
            }
        }
        return adj;
    }
}
