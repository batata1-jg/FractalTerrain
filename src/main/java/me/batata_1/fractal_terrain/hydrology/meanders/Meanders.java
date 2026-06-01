package me.batata_1.fractal_terrain.hydrology.meanders;

import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

public final class Meanders {

    // sampling dist e DT estao muito intimos
    private static final double TWO_THIRDS = 2.0 / 3.0;
    private static final double INF = 1e9;
    private static final double OMEGA           = -1.0;
    // aumenta pra aumentar a varian
    private static final double GAMMA           =  2.5;

    private static final double K               =  0.0164; // aumentar esse faz curvar pra traz
    private static final double FRICTION              =  0.011; // diminue a
    private static final double DT              =  1;
    // mudar pra 50m depois
    public static final double DX =  1;
    private static final double[] UNIT_VECTOR = new double[]{1,1};
    private static final Logger LOG = getLogger(Meanders.class);

    //this model does not remove junctions and channels that have dried out.
    private final int      gridSize;
    private final double[] gradMag;
    private final QuadTree<Channel.ChannelPt> quadTree = new QuadTree<>(new double[]{-INF,-INF},new double[]{INF,INF});
    private final ArrayList<Channel> channels = new ArrayList<>();
    private final ArrayList<Junction> junctions = new ArrayList<>();
    private final double cutOffThreshold;
    private final double maxMigrationMagnetude;

    public record Junction(int[] parents, int child) {

        public boolean isChild(int id) {
                return id == child;
        }

        public boolean contains(int id) {
                for (int i : parents) if (i == id) return true;
                return isChild(id);
        }

    }

    public Meanders(int gridSize, double[] gradMag) {
        this.gridSize      = gridSize;
        this.gradMag       = gradMag;
        cutOffThreshold = 1.5*DX*Math.max(Math.log(Math.sqrt(DT)),1)*Math.max(Math.log(FRICTION*10),1);
        maxMigrationMagnetude = INF;
    }

    public int addChannel(ArrayList<double[]> xzPts, double width) {
        final int id = channels.size();
        channels.add(new Channel(width, xzPts, id));
        junctions.add(null);
        channels.getLast().reSample(DX);
        return id;
    }

    private int addChildChannel(ArrayList<double[]> xzPts, double width) {
        final int id = channels.size();
        channels.add(new Channel(width, xzPts, id));
        junctions.add(null);
        return id;
    }

    public void step(int i) {
        LOG.info("step {}",i);
        quadTree.clear();
        for (Channel ch : channels) {
            migrate(ch);
            manageCutoffs(ch);
        }
      //  manageCollisions();
        ensureJunctionsConnected();
        for(Channel ch : channels) {
            try {
                ch.reSample(DX);
            } catch (IllegalStateException e) {
                LOG.error("channel: {}",ch);
                throw new RuntimeException();
            }
        }
    }

    private void ensureJunctionsConnected() {
        for(final Junction j : junctions) if(j!=null) {
            final double[] childStatingPoint = channels.get(j.child()).spline.points().getFirst();
            for(final int id : j.parents()) {
                final ArrayList<double[]> pts = channels.get(id).spline.points();
                pts.set(pts.size()-1,childStatingPoint);
            }
        }
    }

    public void simulate(int n) {
        for (int i = 1; i <= n; i++) step(i);
    }

    public int getGridSize() { return gridSize; }

    public void addConstraint(double cx, double cz, double radius, double effect) {
        // TODO: PointConstraint gradient influence
    }

    private static double[] computeMigrationRates(Channel ch) {
        final double sinuosity = ch.computeSinuosity();
        final double[] localRates = ch.computeLocalRates();
        Debug.isNan(localRates);
        final double sigmaToTheMinus2over3 = Math.pow(sinuosity,-TWO_THIRDS);
        final double alpha = 2*FRICTION / ch.depth;
        final double expTerm = Math.exp(-alpha*DX);
        double integralTerm=0;
        final double[] migRates = new double[ch.spline.points().size()];
        for(int i = 0; i<ch.spline.points().size() ; i++) {
            integralTerm += localRates[i] * K;
            final double migRate =
                OMEGA * localRates[i] +
                        //remove alpha
                GAMMA * alpha * sigmaToTheMinus2over3 * integralTerm;
            integralTerm *= expTerm;
            migRates[i] = -localRates[i];
        }
        Debug.isNan(migRates);
       // LOG.info("mig rates: {}",migRates);
        return migRates;
    }

    /**
     *   Migrates points to new locations, simulating channel meandering.
     *   Does not migrate the derivatives as well, only the points. It computes the derivatives later
     *   assuming equal distance in parameter space of the spline. Thus providing an approximation.
     */
    public void migrate(Channel ch) {
        final double[] migRates = computeMigrationRates(ch);
        ArrayList<double[]> newPts = new ArrayList<>();
        for (int i = 0; i <ch.spline.points().size(); i++) {
            final double rate = Math.clamp(DT * migRates[i], -maxMigrationMagnetude, maxMigrationMagnetude);
            final double[] migVector = VectorOps.scale(ch.spline.normal(i),-rate);
            double[] newPt = VectorOps.add(ch.spline.points().get(i), migVector);
            Debug.isNan(newPt);
            newPts.add(newPt);
            //LOG.info("rate: {}, migrate: {} , w: {} , normal: {}",-rate,ch.migRates[i],w,normal(ch.pts,i));
        }
        ch.spline = QuinticHermiteSpline.createCatmullRom(newPts);
    }

    private List<Channel.ChannelPt> getPtsCloseTo(final int index ,final Channel.ChannelPt[] pts) {
        Channel ch = channels.get(pts[0].channelId);
        final int id1 = Math.min(index+1,pts.length-1);
        final int id0 = Math.max(index-1,0);
        final double maxDist = Math.max(VectorOps.distance(pts[index].toArray(),pts[id0].toArray()),VectorOps.distance(pts[index].toArray(),pts[id1].toArray()));
        return quadTree.getPointsInBox(
                VectorOps.sub(pts[index].toArray(),VectorOps.scale(UNIT_VECTOR, ch.width)),
                VectorOps.add(pts[index].toArray(),VectorOps.scale(UNIT_VECTOR, ch.width))
        );
    }

    private List<Channel.ChannelPt> getPtsCloseTo(Channel.ChannelPt pt) {
        return quadTree.getPointsInBox(
                VectorOps.sub(pt.toArray(),VectorOps.scale(UNIT_VECTOR, channels.get(pt.channelId).width)),
                VectorOps.add(pt.toArray(),VectorOps.scale(UNIT_VECTOR, channels.get(pt.channelId).width))
        );
    }

    private void manageCutoffs(Channel ch) {
        if(ch.spline.checkNaN()) {
            throw new RuntimeException("cannot cut becuse spline is NaN");
        }
        quadTree.clear();
        insertChannel(ch);
        ArrayList<Integer> newPathIndexes = new ArrayList<>();

        int maxListSize = 0;

        for(int id = 0; id<ch.numPts()-1 ; id++) {
            if(!quadTree.containsPoint(ch.pt(id))) continue;
            newPathIndexes.add(id);
            List<Channel.ChannelPt> ptList = getPtsCloseTo(ch.pt(id));
            ptList.sort(null);
            maxListSize = Math.max(maxListSize, ptList.size());
            for(Channel.ChannelPt cpt : ptList) {
                if(cpt.index<=id+1||cpt.channelId!=ch.channelId) continue;
                LOG.info("cutting from {} to {}",id,cpt.index);
              //  cutRiverSection(id,cpt.index,ch);
            }
        }
        newPathIndexes.add(ch.numPts()-1);
        LOG.info("manage cutoffs new pts: {}",newPathIndexes);
        ch.keepOnly(newPathIndexes);
        //LOG.info("after cuttoff {} list size:{}",ch.spline.getMaxT(),maxListSize);
    }

    //TODO:rewrite this accumulate indexes to remove and new pts to add, make all of the changes after this;
    private void manageCollisions() {
        quadTree.clear();
        final HashMap<Integer,ArrayList<Integer>> removedIds = new HashMap<>();
        for(Channel ch: channels) {
            removedIds.put(ch.channelId,new ArrayList<>());
            insertChannel(ch);
        }
        for(int chId = 0; chId <channels.size() ; chId++) {
            if(!removedIds.containsKey(chId)) removedIds.put(chId,new ArrayList<>());
            final Channel ch = channels.get(chId);
            pointTraversal:
            for(int ptId = 0 ; ptId<ch.numPts() ; ptId++) if(quadTree.containsPoint(ch.pt(ptId))) {
                List<Channel.ChannelPt> closePts = getPtsCloseTo(ch.pt(ptId));
                closePts.sort(null);
                for(Channel.ChannelPt close : closePts) if(close.channelId!=chId) {
                    final int colId = close.channelId;
                    final Channel collided = channels.get(colId);
                    //connected collision
                    if (junctions.get(chId) != null) {
                        if (junctions.get(chId).contains(colId)) {
                            Junction junction = junctions.get(chId);
                            // downstream collision:
                            // assumes there are only two parents
                            if (junction.isChild(colId)) {
                                ArrayList<Integer> removedFromCh = cutRiverSection(ptId, ch.numPts(), ch);
                                removedIds.get(chId).addAll(removedFromCh);
                                ch.add(close, collided);
                                ArrayList<Integer> removedFromChild = cutRiverSection(0, close.index, collided);
                                removedIds.get(colId).addAll(removedFromChild);
                                final int parentId = junction.parents[0] == chId ? junction.parents[1] : junction.parents[0];
                                final Channel parent = channels.get(parentId);
                                addSection(removedFromChild, collided, parent);
                                parent.add(close, collided);
                                break pointTraversal;
                            }
                            // upstream collision:
                            ArrayList<Integer> removedFromCh = cutRiverSection(ptId, ch.numPts(), ch);
                            removedIds.get(chId).addAll(removedFromCh);
                            ArrayList<Integer> removedFromCollided = cutRiverSection(close.index, collided.numPts(), collided);
                            removedIds.get(colId).addAll(removedFromCollided);
                            if (ch.width >= collided.width) {
                                collided.add(ptId, ch);
                            } else {
                                ch.add(close.index, collided);
                            }
                            final int childId = junction.child;
                            final Channel child = channels.get(childId);
                            removeChannel(child);
                            if (ch.width >= collided.width) {
                                for (int i = removedFromCh.size() - 1; i >= 0; i--)
                                    child.addFront(removedFromCh.get(i), ch);
                            } else {
                                for (int i = removedFromCollided.size() - 1; i >= 0; i--)
                                    child.addFront(removedFromCollided.get(i), collided);
                            }
                            insertChannel(child);
                            break pointTraversal;
                        }
                    }
                    if (junctions.get(colId) != null) {
                        if(junctions.get(colId).isChild(chId)) {
                            Junction junction = junctions.get(colId);
                            //ch is the child
                            ArrayList<Integer> removedFromChild = cutRiverSection(0, ptId, ch);
                            removedIds.get(chId).addAll(removedFromChild);
                            ArrayList<Integer> removedFromCollided = cutRiverSection(close.index, collided.numPts(), collided);
                            removedIds.get(colId).addAll(removedFromCollided);
                            final int parentId = junction.parents[0] == colId ? junction.parents[1] : junction.parents[0];
                            final Channel parent = channels.get(parentId);
                            addSection(removedFromChild, ch, parent);
                            parent.add(ptId, ch);
                            break pointTraversal;
                        }
                    }

                    //disconnected collision
                    ArrayList<Integer> removedFromCh = cutRiverSection(ptId, ch.numPts(), ch);
                    removedIds.get(chId).addAll(removedFromCh);
                    ArrayList<Integer> removedFromCollided = cutRiverSection(close.index, collided.numPts(), collided);
                    removedIds.get(colId).addAll(removedFromCollided);
                    // add channels with previous tangents
                    final int newId = addChildChannel(new ArrayList<>(),Math.sqrt(ch.width*ch.width+collided.width*collided.width));
                    Junction newJunction = new Junction(new int[]{chId,colId},newId);
                    junctions.set(chId,newJunction);
                    junctions.set(colId,newJunction);
                    junctions.add(null);
                    if(ch.width>=collided.width) {
                        channels.get(newId).add(ptId,ch);
                        for(int id : removedFromCh) channels.get(newId).add(id,ch);
                    } else {
                        channels.get(newId).add(close.index,collided);
                        for(int id : removedFromCollided) channels.get(newId).add(id,collided);
                    }
                    break pointTraversal;
                }
            }
        }

       // for(Channel ch : channels) ch.removeIndexes(removedIds.get(ch.channelId));

    }

    /**
     * adds all but the first element of the section to the channel. It appends from the last index of the channel
     * */
    private void addSection(ArrayList<Integer> section, Channel from, Channel to) {
        for(int i=1 ; i< section.size() ; i++) to.add(section.get(i),from);
    }

    private void insertChannel(Channel ch) {
        Channel.ChannelPt[] pts = ch.getChannelAsPts();
        for(Channel.ChannelPt pt : pts) {
            quadTree.insertPoint(pt);
//            LOG.info("contem esse {} ? {}",pt,quadTree.containsPoint(pt));
//            LOG.info("contem {} ? {}",ch.pt(pt.index),quadTree.containsPoint(ch.pt(pt.index)));
        }
      //  for(int id=0 ; id<ch.numPts()-1 ; id++) LOG.info("contem {} ? {}",ch.pt(id),quadTree.containsPoint(ch.pt(id)));
    }

    private void removeChannel(Channel ch) {
        Channel.ChannelPt[] pts = ch.getChannelAsPts();
        for(Channel.ChannelPt pt : pts) quadTree.removePoint(pt);
    }

    /**
     *  returns the indexes of the cut channel does not remove index {@code to}
     * */
    private ArrayList<Integer> cutRiverSection(int from, int to, Channel ch) {
        final ArrayList<Integer> res = new ArrayList<>();
        for(int i=from; i<to ; i++) {
            res.add(i);
            quadTree.removePoint(ch.pt(i));
        }
        return res;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static double bilinearSample(double[] src, int H, int W, double gy, double gx) {
        double y  = Math.clamp(gy, 0.0, H - 1.0);
        double x  = Math.clamp(gx, 0.0, W - 1.0);
        int    y0 = (int) y, y1 = Math.min(H - 1, y0 + 1);
        int    x0 = (int) x, x1 = Math.min(W - 1, x0 + 1);
        double wy = y - y0,  wx = x - x0;
        return (1-wy)*(1-wx)*src[y0*W+x0]
             + (1-wy)*wx    *src[y0*W+x1]
             + wy    *(1-wx)*src[y1*W+x0]
             + wy    *wx    *src[y1*W+x1];
    }

    public List<Channel> getChannels() {
        return channels;
    }

    public ArrayList<double[]> getChannelPts(int i) {
        return channels.get(i).spline.points();
    }
}
