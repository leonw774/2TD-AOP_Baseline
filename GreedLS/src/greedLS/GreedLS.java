package greedLS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
//import javax.net.ssl.HttpsURLConnection;


/**
 * @author Ying Lu <ylu720@usc.edu>
 * <p>
 * Implementation of the Greedy Local Search (GreedLS) algorithm.
 * @Idea: Iteratively insert promising feasible arcs into the solution path
 * until all the travel time budget is consumed.
 */


public class GreedLS {
//    private final static String adjFile = "Graph/sampled-LAStaticData-Arcs.txt";
//    private final static String nodeFile = "Graph/LAStaticData-Nodes.csv";
    private final static String adjFile = "Graph/Arcs.txt"; /* MODIFIED */
    private final static String nodeFile = "Graph/Nodes.csv"; /* MODIFIED */
    private static PrintWriter outputWriter;

    public final static Graph graph = new Graph(adjFile, nodeFile);
    public final static int costGranularity = 1000 * 60 * 15; //15 mins
    public final static int valueGranularity = 1000 * 60 * 15; //15 mins
    public static int iterationNUM = 0;

    /**
     * Used for cost normalization, calculated during graph loading
     */
    public static int costMAX = 1285272;

    /**
     * Used for value normalization, calculated during graph loading
     */
    public static int valueMAX = 1431;

    /**
     * Used for Euclidean distance pruning, millisec
     */
    public static double speedMAX = 662.7946059169074;

//    public static double speedMIN = 15.615478233558985; //Double.MAX_VALUE;
    public static double speedMIN = 0.0; //Double.MAX_VALUE; /* MODIFIED: we don't use distance pruning */
    public static double speedAVG = 327.81159775135086;
    private static int curMaxScenicValue = 0 - Integer.MAX_VALUE;
    private static int[] optimalValue = new int[4];
    private static int optimalValueIdx = 0;

    private FindTDSP findTDSP = new FindTDSP();

    Solution solution = new Solution(); //always the current solution
    public long programStartTime = 0;
    public long programCurTime = 0;
    private static long calculateCandArcTime = 0;
    private static long arcSelectionTime = 0;
    private static long arcInsertionTime = 0;
    private static long printingTime;

    /**
     * Record the feasible / candidate arcs generated at each iteration.
     */
    private Set<Arc> CurIntersectionArc; //G'

    /**
     * Record the feasible vertices calculated at current iteration.
     */
    private Map<Integer, Pair<Integer, Integer>> CurverticeEALD_submap =
            new HashMap<Integer, Pair<Integer, Integer>>(); //V'

    /**
     * Record the feasible vertices calculated so far.
     * Used for ``inherit''-pruning
     */
    public Map<Integer, Pair<Integer, Integer>> verticeEALD_map =
            new HashMap<Integer, Pair<Integer, Integer>>(); //V''

    /**
     * Record the feasible arcs calculated so far.
     */
    public Set<Arc> CAS = new HashSet<Arc>(); //G''

    //public Set<Pair<Integer,Integer>> CAS = new HashSet<Pair<Integer,Integer>>(); //G''
    public Map<Integer, Pair<Integer, Integer>> tempVerticeEALD_map =
            new HashMap<Integer, Pair<Integer, Integer>>(); //V''

    public Set<Arc> tempCAS = new HashSet<Arc>(); //G''

    public String startSolution = "NULL";

    /**
     * Used to record the EA and LD for the first iteration for buffering.
     */
    private Map<Integer, EALDbuffer_MapValue> EALDBuffer =
            new HashMap<Integer, EALDbuffer_MapValue>();


    /**
     * Convert timecost to time index
     *
     * @param timecost: milliseconds from 6:00am
     * @return
     */
    public static int TimeCost2Idx(int timecost) {
        int timeIdx = timecost / costGranularity;
        if (timeIdx >= 60) timeIdx = 59;
        if (timeIdx < 0) timeIdx = 0;
        return timeIdx;
    }


    /**
     * Convert time index to timecost
     *
     * @param idx: time index
     * @return
     */
    public static int Idx2TimeCost(int idx) {
        int timecost = idx * costGranularity;
        return timecost;
    }


    /**
     * Calculate the distance between two points on the Earth surface.
     *
     * @param (plat,plng): the lantitude and longitude of a point p. Range: (-90,90) in degree.
     * @param (qlat,qlng): the lantitude and longitude of a point q. Range: (-180, 180) in degree.
     * @return the distance between p and q [meter].
     */
    public double EarthDistance(double plat, double plng, double qlat, double qlng) {
        double PI = 3.1415926;
        double distpq = 6371 * 2 * Math.asin(Math.sqrt(Math.pow(Math.sin((plat - qlat) * PI / 180 / 2), 2)
                + Math.cos(plat * PI / 180) * Math.cos(qlat * PI / 180)
                * Math.pow(Math.sin((plng - qlng) * PI / 180 / 2), 2)));
        distpq = distpq * 1000; //kilometers --> meters
        return distpq;
    }


    /**
     * Initialize CAS and verticeEALD_map
     *
     * @param graphArcList
     */
    public void copyFromGraph() {
        Arc arc;
        int i = 0;
        for (Map.Entry<Integer, Map<Integer, Pair<Integer, Integer>>> entry :
                GreedLS.graph.adjList.adjacencyList.entrySet()) {

            for (Map.Entry<Integer, Pair<Integer, Integer>> pair :
                    entry.getValue().entrySet()) {
                i++;
                arc = new Arc();
                arc.source = entry.getKey();
                arc.target = pair.getKey();
                arc.cost_value_list = pair.getValue();
                this.CAS.add(arc);
                if (!this.verticeEALD_map.containsKey(arc.source)) {
                    this.verticeEALD_map.put(arc.source, new Pair<Integer, Integer>
                            (GreedLS.Idx2TimeCost(QuerySetting.startTime),
                                    GreedLS.Idx2TimeCost(QuerySetting.startTime) + QuerySetting.budgetTime));
                }
                if (!this.verticeEALD_map.containsKey(arc.target)) {
                    this.verticeEALD_map.put(arc.target, new Pair<Integer, Integer>
                            (GreedLS.Idx2TimeCost(QuerySetting.startTime),
                                    GreedLS.Idx2TimeCost(QuerySetting.startTime) + QuerySetting.budgetTime));
                }
            }
        }
    }


    /**
     * Initialize the solution path.
     */
    public void initSolution() throws Exception {
        Gap g = new Gap();
        g = findTDSP.tdsp(QuerySetting.SourceVexID,
                QuerySetting.TargetVexID,
                QuerySetting.startTime);
        this.solution.gapList.add(g);
        //--start from the shortest path
        if (this.startSolution == "SP") {
            this.solution.totalCost = g.SPCost;
            this.solution.totalValue = g.collectedValue;
        }
    }


    /**
     * Return the earliest arrival entry <travel time, collected value>.
     */
    private Map.Entry<Integer, Integer> getEarliestArriveEntry(Map<Integer, Integer> map) {
        Map.Entry<Integer, Integer> minEA = null;
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (minEA == null || minEA.getValue() > entry.getValue()) {
                minEA = entry;
            }
        }
        return minEA;
    }


    /**
     * Return the latest departure <travel time, collected value>.
     */
    private Map.Entry<Integer, Integer> getLatestDepartureEntry(Map<Integer, Integer> map) {
        Map.Entry<Integer, Integer> maxLD = null;
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (maxLD == null || maxLD.getValue() < entry.getValue()) {
                maxLD = entry;
            }
        }
        return maxLD;
    }


    /**
     * Calculate the feasible arcs:
     * 1) FWR: Perform the forward search to calculate the forward-reachable vertices;
     * 2) BWR: Perform the backward search to calculate the backward-reachable vertices;
     * 3) Calculate the vertices that are both forward and backward reachable vertices.
     *
     * @param gap: the gap in the solution path where arcs are inserted into.
     * @param b:   the remaining budget.
     */
    public void calculateCandidateArcSet(Gap gap, int b) throws Exception {
        Map<Integer, Integer> FWR_result = FWR(gap.start, gap.actualStarttime, b);
        if (GreedLS.iterationNUM == 1) {
            for (Map.Entry<Integer, Integer> fwr_ret : FWR_result.entrySet()) {
                EALDbuffer_MapValue mapval = new EALDbuffer_MapValue();
                mapval.setEA(fwr_ret.getValue() - GreedLS.Idx2TimeCost(QuerySetting.startTime));
                this.EALDBuffer.put(fwr_ret.getKey(), mapval);
            }
        }


        Map<Integer, Integer> BWR_result = BWR(gap.end, gap.actualStarttime, b);
        if (this.CurverticeEALD_submap != null) this.CurverticeEALD_submap.clear();
        if (this.CurIntersectionArc != null) this.CurIntersectionArc.clear();

        //calculate the intersection vertices
        this.CurverticeEALD_submap = new HashMap<Integer, Pair<Integer, Integer>>(); //V'
        for (Map.Entry<Integer, Integer> entryf : FWR_result.entrySet()) {
            if (BWR_result.containsKey(entryf.getKey())) {
                int ld = BWR_result.get(entryf.getKey());
                if (entryf.getValue() <= ld && !this.CurverticeEALD_submap.containsKey(entryf.getKey())) {
                    this.CurverticeEALD_submap.put(entryf.getKey(),
                            new Pair<Integer, Integer>(entryf.getValue(), ld));

                    if (GreedLS.iterationNUM == 1) {
                        EALDbuffer_MapValue mapval = this.EALDBuffer.get(entryf.getKey());
                        mapval.setLD(GreedLS.Idx2TimeCost(QuerySetting.startTime) +
                                QuerySetting.budgetTime - ld);
                        this.EALDBuffer.replace(entryf.getKey(), mapval);
                    }
                } else this.EALDBuffer.remove(entryf.getKey());
            } else this.EALDBuffer.remove(entryf.getKey());
        }
        /** If exist already, then update value automatically */
        this.tempVerticeEALD_map.putAll(this.CurverticeEALD_submap);

        //calculate the intersection arcs
        this.CurIntersectionArc = new HashSet<Arc>(); //G'
        for (Map.Entry<Integer, Pair<Integer, Integer>> ventry : this.CurverticeEALD_submap.entrySet()) {
            for (Map.Entry<Integer, Pair<Integer, Integer>> target :
                    GreedLS.graph.adjList.adjacencyList.get(ventry.getKey()).entrySet()) {
                Arc arc = new Arc();
                arc.source = ventry.getKey();
                arc.target = target.getKey();
                arc.cost_value_list = target.getValue();
                if (this.CurverticeEALD_submap.containsKey(target.getKey())
                        && !this.CurIntersectionArc.contains(arc)) {
                    this.CurIntersectionArc.add(arc);
                }
            }
        }
        this.tempCAS.addAll(this.CurIntersectionArc);
    }


    /**
     * calculate the earliest arrive time for each candidate arc
     *
     * @param CAQ: candidate arc queue
     * @param v0:  starting vertex
     * @param t0:  starting time
     * @param b:   budget
     */
    public Map<Integer, Integer> FWR(int vid0, int t0, int b) throws Exception {
        Map<Integer, Integer> Q = new HashMap<Integer, Integer>();
        Map<Integer, Integer> result = new HashMap<Integer, Integer>();
        Q.put(vid0, t0);
        result.put(vid0, t0);
        while (!Q.isEmpty()) {
            Map.Entry<Integer, Integer> entryEA = getEarliestArriveEntry(Q);
            int vi = entryEA.getKey();
            int eai = entryEA.getValue();
            Q.remove(vi, eai);
            if (GreedLS.graph.adjList.adjacencyList.containsKey(vi)) {
                for (Map.Entry<Integer, Pair<Integer, Integer>> vjpair :
                        GreedLS.graph.adjList.adjacencyList.get(vi).entrySet()) {
                    /** Reduce the search space, search from V''
                     * Inherit technique.
                     */
                    if (this.verticeEALD_map.containsKey(vjpair.getKey())) {
                        int eaj = eai + (vjpair.getValue()).getLeft();
                        /** Not apply EALD-pruning yet. */
                        if (eaj < t0 + b) {//----within the budget
                            /* BELOW MODIFIED: we don't use distance pruning */
//                            double vj_target_dist = this.EarthDistance(
//                                    GreedLS.graph.vertices.get(QuerySetting.TargetVexID).lat,
//                                    GreedLS.graph.vertices.get(QuerySetting.TargetVexID).lng,
//                                    GreedLS.graph.vertices.get(vjpair.getKey()).lat,
//                                    GreedLS.graph.vertices.get(vjpair.getKey()).lng);
                            double vj_target_dist = 0;
                            /* ABOVE MODIFIED */
                            /** Euclidean distance based pruning (or A* pruning or FWEST-pruning. */
                            if (eaj + vj_target_dist * GreedLS.speedMIN < t0 + b) {
                                EALDbuffer_MapValue buffer = new EALDbuffer_MapValue();
                                if (this.EALDBuffer.containsKey(vjpair.getKey())) {
                                    buffer = this.EALDBuffer.get(vjpair.getKey());
                                }
                                /** Buffer pruning */
                                if ((eaj - GreedLS.Idx2TimeCost(QuerySetting.startTime) + buffer.LDCost <= b)) {
                                    if (!result.containsKey(vjpair.getKey())) {
                                        result.put(vjpair.getKey(), eaj);
                                        if (!Q.containsKey(vjpair.getKey())) Q.put(vjpair.getKey(), eaj);
                                        else {
                                            int old_eaj = Q.get(vjpair.getKey());
                                            if (old_eaj > eaj) Q.replace(vjpair.getKey(), eaj);
                                        }
                                    } else {
                                        int old_eaj = result.get(vjpair.getKey());
                                        if (old_eaj > eaj) {
                                            result.replace(vjpair.getKey(), old_eaj, eaj);
                                            if (Q.containsKey(vjpair.getKey())) {
                                                Q.replace(vjpair.getKey(), eaj);
                                            }
                                        }
                                    }
                                }//----buffer pruning
                            }//----A* pruning
                        }
                    }
                }
            }

        }//end while
        return result;
    }


    /**
     * Calculate the latest leaving time for each candidate arc
     *
     * @param CAQ: candidate arc queue
     * @param v0:  starting vertex
     * @param t0:  starting time
     * @param b:   budget
     */
    public Map<Integer, Integer> BWR(int vidN, int t0, int b) throws Exception {
        Map<Integer, Integer> Q = new HashMap<Integer, Integer>();
        Map<Integer, Integer> result = new HashMap<Integer, Integer>();
        int tN = t0 + b;
        Q.put(vidN, tN);
        result.put(vidN, t0 + b);
        while (!Q.isEmpty()) {
            Map.Entry<Integer, Integer> entryLD = getLatestDepartureEntry(Q);
            int vj = entryLD.getKey();
            int ldj = entryLD.getValue();
            Q.remove(vj, ldj);
            if (GreedLS.graph.adjList.reverse_adjacencyList.containsKey(vj)) {
                for (Map.Entry<Integer, Pair<Integer, Integer>> vipair :
                        GreedLS.graph.adjList.reverse_adjacencyList.get(vj).entrySet()) { //reverse adjacency list
                    int vi = vipair.getKey();
                    /** Reduce the search space, search from V''
                     * Inherit technique.
                     */
                    if (this.verticeEALD_map.containsKey(vi)) {
                        /**
                         * To find maximum time index Taok of the list
                         * L=vipair.getRight() such that L[Taok]+Taok <= ldj,
                         * and then ldi=ldj-L[Taok].
                         */
                        int Taok = ldj;
                        for (; Taok + vipair.getValue().getLeft() < ldj; Taok = Taok - GreedLS.costGranularity) ;
                        int ldi = ldj - vipair.getValue().getLeft();

                        /** Not apply EALD-pruning yet. */
                        if (ldi > t0) { //---within the budget
                            /* BELOW MODIFIED: we don't use distance pruning */
//                            double source_vi_dist = this.EarthDistance(
//                                    GreedLS.graph.vertices.get(QuerySetting.SourceVexID).lat,
//                                    GreedLS.graph.vertices.get(QuerySetting.SourceVexID).lng,
//                                    GreedLS.graph.vertices.get(vi).lat,
//                                    GreedLS.graph.vertices.get(vi).lng);
                            double source_vi_dist = 0;
                            /* ABOVE MODIFIED */
                            /** Euclidean distance based pruning (or A* pruning or FWEST-pruning. */
                            if (ldi - source_vi_dist * GreedLS.speedMIN > t0) {
                                EALDbuffer_MapValue buffer = new EALDbuffer_MapValue();
                                if (this.EALDBuffer.containsKey(vipair.getKey())) {
                                    buffer = this.EALDBuffer.get(vipair.getKey());
                                }
                                /** Buffer pruning */
                                if ((buffer.EACost + (t0 + b - ldi) <= b)) {
                                    if (!result.containsKey(vipair.getKey())) {
                                        result.put(vipair.getKey(), ldi);
                                        if (!Q.containsKey(vipair.getKey())) Q.put(vipair.getKey(), ldi);
                                        else {
                                            int old_ldi = result.get(vipair.getKey());
                                            if (old_ldi < ldi) Q.replace(vipair.getKey(), ldi);
                                        }
                                    } else {
                                        int old_ldi = result.get(vipair.getKey());
                                        if (old_ldi < ldi) {
                                            result.replace(vipair.getKey(), old_ldi, ldi);
                                            if (Q.containsKey(vipair.getKey())) {
                                                Q.replace(vipair.getKey(), ldi);
                                            }
                                        }
                                    }
                                }//----buffer pruning
                            }//---A* pruning

                        }//---within the budget
                    }
                }
            }

        }//end while
        return result;
    }


    /**
     * To calculate the criteria / ratio if inserting the arc into
     * the gap in the solution, based on the TDSP distance.
     *
     * @param arc: a candidate arc that can insert into the gap
     * @param gap: a gap in the solution
     * @return: the calculated criteria
     */
    public double calTDSPCriteria(Gap gap, Arc arc) {
        Vertex vi = GreedLS.graph.vertices.get(gap.start);
        Vertex vj = GreedLS.graph.vertices.get(gap.end);
        Vertex vm = GreedLS.graph.vertices.get(arc.source);
        Vertex vn = GreedLS.graph.vertices.get(arc.target);

        int starttime_vi_vm = gap.actualStarttime;
        Gap vi_vm = findTDSP.tdsp(vi.getId(), vm.getId(), GreedLS.TimeCost2Idx(starttime_vi_vm));
        int starttime_vn_vj_int = starttime_vi_vm + vi_vm.SPCost;
        Gap vn_vj = findTDSP.tdsp(vn.getId(), vj.getId(), GreedLS.TimeCost2Idx(starttime_vn_vj_int));

        Pair<Integer, Integer> cost_value_list_vm_vn = GreedLS.graph.adjList.adjacencyList.get(arc.source).get(arc.target);
        int new_value = vi_vm.collectedValue + vn_vj.collectedValue + cost_value_list_vm_vn.getRight();
        int deltaValue = new_value - gap.collectedValue;
        int new_cost = vi_vm.SPCost + vn_vj.SPCost + cost_value_list_vm_vn.getLeft();
        int deltaCost = new_cost - gap.SPCost;
        double criteria = ((double) deltaValue / GreedLS.valueMAX) / ((double) deltaCost / GreedLS.costMAX);
        return criteria;
    }


    /**
     * To calculate the criteria / ratio if inserting the arc into
     * the gap in the solution, based on the Euclidean distance.
     *
     * @param arc: a candidate arc that can insert into the gap
     * @param gap: a gap in the solution
     * @return: the calculated criteria
     */
    public double calEuclideanCriteria(Gap gap, Arc arc) {
        Vertex vi = GreedLS.graph.vertices.get(gap.start);
        Vertex vj = GreedLS.graph.vertices.get(gap.end);
        Vertex vm = GreedLS.graph.vertices.get(arc.source);
        Vertex vn = GreedLS.graph.vertices.get(arc.target);

        double vi_vm_dist = this.EarthDistance(vi.getLat(), vi.getLng(), vm.getLat(), vm.getLng());
        double vn_vj_dist = this.EarthDistance(vn.getLat(), vn.getLng(), vj.getLat(), vj.getLng());
        //int starttime_vi_vm = this.CurverticeEALD_submap.get(vi.id).getLeft();
        int starttime_vi_vm = gap.actualStarttime;
        int new_value = GreedLS.graph.adjList.adjacencyList.get(vm.getId()).get(vn.getId()).getRight();
        int deltaValue = new_value - gap.collectedValue;
        int new_cost = (int) ((vi_vm_dist + vn_vj_dist) * GreedLS.speedAVG) +
                GreedLS.graph.adjList.adjacencyList.get(vm.getId()).get(vn.getId()).getLeft();
        int deltaCost = new_cost - gap.SPCost;
        double criteria = ((double)new_value/GreedLS.valueMAX) / ((double)new_cost/GreedLS.costMAX);
        return criteria;
    }


    /**
     * Print the solution path.
     */
    public void printSolution() {
        double valuePercent = (double) this.solution.totalValue;
        GreedLS.outputWriter.print("solution: total value: " +
                this.solution.totalValue +
                "%\ttotal cost: " +
                this.solution.totalCost);
		  /* for(Gap gap : this.solution.gapList){ //---print the path of the solution
			  System.out.print(", " + gap.start + " --- " + gap.end);
		  } */
        //System.out.println();
        GreedLS.outputWriter.println();
    }


    /**
     * Initialize the solution path.
     */
    public Boolean InSolution(Arc arc) {
        Gap pre_gap = new Gap();
        for (Gap gap : this.solution.gapList) {
            if (pre_gap.start != -1) {
                if (pre_gap.end == arc.source && gap.start == arc.target) return true;
            }
            pre_gap = gap;
            //--check for the vertex list
            int preVid = -1;
            for (int vid : gap.vexIDList) {
                if (preVid != -1) {//remove from G''
                    if (preVid == arc.source && vid == arc.target) return true;
                }//else do nothing
                preVid = vid;
            }
        }
        return false;
    }


    public void ArrageSolution() {
        //====calculate the average criteria, generate arcList.
        List<solutionArc> arcList = new LinkedList<solutionArc>();
        Gap gap1 = new Gap();
        int timegap = GreedLS.Idx2TimeCost(QuerySetting.startTime);
        int idx = 0;
        double average = 0;
        for (Gap gap2 : this.solution.gapList) {
            if (!gap1.isEmpty()) {
                if (GreedLS.graph.adjList.adjacencyList.get(gap1.end) != null) {
                    if (GreedLS.graph.adjList.adjacencyList.get(gap1.end).get(gap2.start) != null) {
                        int value = GreedLS.graph.adjList.adjacencyList.get(gap1.end).get(gap2.start).getRight();
                        int cost = GreedLS.graph.adjList.adjacencyList.get(gap1.end).get(gap2.start).getLeft();
                        timegap += cost;
                        arcList.add(new solutionArc(gap1.end, gap2.start, cost, value));
                        average += (double) value / (double) cost;
                        idx++;
                    }
                }
            }
            gap1 = gap2;

            int prev = -1;
            for (int v : gap2.vexIDList) {
                if (prev != -1) {
                    int value = GreedLS.graph.adjList.adjacencyList.get(prev).get(v).getRight();
                    int cost = GreedLS.graph.adjList.adjacencyList.get(prev).get(v).getLeft();
                    timegap += cost;
                    arcList.add(new solutionArc(prev, v, cost, value));
                    average += (double) value / (double) cost;
                    idx++;
                }
                prev = v;
            }
        }
        average = average / idx;

        //===re-segment the solution, generate newSol
        Solution newSol = new Solution();
        newSol.totalCost = 0;
        newSol.totalValue = 0;
        int time = GreedLS.Idx2TimeCost(QuerySetting.startTime);
        Gap g = new Gap();
        g.start = QuerySetting.SourceVexID;
        g.actualStarttime = time;
        g.SPCost = 0;
        g.collectedValue = 0;
        idx = 0;
        for (solutionArc a : arcList) {
            double a_criteria = (double) a.value / (double) a.cost;
            if (a_criteria > average) {
                g.end = a.source;
                g.vexIDList.add(a.source);
                newSol.gapList.add(g);
                newSol.totalCost += g.SPCost;
                newSol.totalValue += g.collectedValue;
                newSol.totalCost += a.cost;
                newSol.totalValue += a.value;
                g = new Gap();
                g.start = a.target;
                g.actualStarttime = time;
                g.SPCost = 0;
                g.collectedValue = 0;
            } else {
                g.vexIDList.add(a.source);
                g.SPCost += a.cost;
                g.collectedValue += a.value;
                if (idx == arcList.size() - 1) {
                    g.end = a.target;
                    g.vexIDList.add(a.target);
                    newSol.gapList.add(g);
                    newSol.totalCost += g.SPCost;
                    newSol.totalValue += g.collectedValue;
                }
            }
            time += a.cost;
            idx++;
        }

        /**
         * For each gap in newSol, choose the gap from newSol.gap
         * and SPgap with smaller cost. generate the final new solution.
         */
        this.solution.empty();
        gap1.Empty();
        timegap = GreedLS.Idx2TimeCost(QuerySetting.startTime);
        for (Gap gap2 : newSol.gapList) {
            Gap newSP = findTDSP.tdsp(gap2.start, gap2.end, GreedLS.TimeCost2Idx(timegap));
            if (newSP.SPCost < gap2.SPCost && this.solution.totalCost + newSP.SPCost < QuerySetting.budgetTime) {
                this.solution.gapList.add(newSP);
                timegap += newSP.SPCost;
                this.solution.totalCost += newSP.SPCost;
                this.solution.totalValue += newSP.collectedValue;
            } else if (this.solution.totalCost + gap2.SPCost < QuerySetting.budgetTime) {
                this.solution.gapList.add(gap2);
                timegap += gap2.SPCost;
                this.solution.totalCost += gap2.SPCost;
                this.solution.totalValue += gap2.collectedValue;
            }


            if (!gap1.isEmpty()) {
                if (GreedLS.graph.adjList.adjacencyList.get(gap1.end) != null) {
                    int value = GreedLS.graph.adjList.adjacencyList.get(gap1.end).get(gap2.start).getRight();
                    int cost = GreedLS.graph.adjList.adjacencyList.get(gap1.end).get(gap2.start).getLeft();
                    if (GreedLS.graph.adjList.adjacencyList.get(gap1.end).get(gap2.start) != null
                            && this.solution.totalCost + cost < QuerySetting.budgetTime) {
                        timegap += cost;
                        this.solution.totalCost += cost;
                        this.solution.totalValue += value;
                    }
                }
            }
            gap1 = gap2;
        }

    }


    /**
     * Remove arc in the path in the gap g from the tempArc
     */
    public void removePathFromtempArc(Gap g) {
        int preVid = -1;
        Arc arc;
        for (int vid : g.vexIDList) {
            //if(this.tempVerticeEALD_map!=null) this.tempVerticeEALD_map.remove(vid); //remove from V''
            if (preVid != -1) {//remove from G''
                arc = new Arc();
                arc.source = preVid;
                arc.target = vid;
                arc.cost_value_list = GreedLS.graph.adjList.adjacencyList.get(preVid).get(vid);
                this.tempCAS.remove(arc);
            }//else do nothing
            preVid = vid;
        }
    }


    /**
     * One iteration, i.e., find and insert one arc into the solution.
     * 1) Calculate the feasible / canddiate arcs.
     * 2) Select the most promising arc from the feasible arcs.
     * 3) Insert the most promising arc into the solution.
     */
    public void GreedLSAlgorithm_OneIteration() throws Exception {//_Euclidean

        /**
         * Insert arcs.
         */
        PriorityQueue<Pair<Double, Pair<Arc, Gap>>> arc_gap_queue =
                new PriorityQueue<Pair<Double,
                        Pair<Arc, Gap>>>(10,
                        new Comparator<Pair<Double,
                                Pair<Arc, Gap>>>() {
                            public int compare(Pair<Double, Pair<Arc, Gap>> p1,
                                               Pair<Double, Pair<Arc, Gap>> p2) {
                                if (p1.getLeft() > p2.getLeft()) return -1;
                                if (p1.getLeft().equals(p2.getLeft())) return 0;
                                return +1;
                            }
                        });

        this.tempCAS.clear();
        this.tempVerticeEALD_map.clear();

        for (Gap gap : this.solution.gapList) {
            //======candidate calculation
            programCurTime = System.currentTimeMillis();
            this.calculateCandidateArcSet(gap, QuerySetting.budgetTime);
            GreedLS.calculateCandArcTime += System.currentTimeMillis() - programCurTime;
            //======arc selection
            programCurTime = System.currentTimeMillis();
            for (Arc candArc : this.CurIntersectionArc) {
                if (this.InSolution(candArc)) continue;
                double criteria = this.calEuclideanCriteria(gap, candArc); //Euclidean selection

                /* Rank the arcs by their criteria */
                arc_gap_queue.add(new Pair<Double, Pair<Arc, Gap>>
                        (criteria, new Pair<Arc, Gap>(candArc, gap)));
            }
            GreedLS.arcSelectionTime += System.currentTimeMillis() - programCurTime;
        }
        programCurTime = System.currentTimeMillis();
        GreedLS.outputWriter.print("G size: " + this.tempCAS.size() + "\t");
        GreedLS.printingTime += System.currentTimeMillis() - programCurTime;

        /**
         * Insert arcs into the solution in order of the criteria
         * until all the budget is consumed.
         */
        programCurTime = System.currentTimeMillis();
        int insertNUM = 0;
        while (this.solution.totalCost <= QuerySetting.budgetTime
                && arc_gap_queue.size() > 0) {//&& insertNUM<3
            insertNUM++;
            Pair<Double, Pair<Arc, Gap>> arc_gap_pair = arc_gap_queue.poll();
            if (arc_gap_pair == null) continue;

            /**
             * To find the closestGap within the gap in arc_gap_pair in the solution.
             */
            Gap closestGap = new Gap();
            double bestCriteria = 0 - Double.MAX_VALUE;
            Boolean trigger = false;
            for (Gap solgap : this.solution.gapList) {
                if (solgap.start == arc_gap_pair.getRight().getRight().start) {
                    trigger = true;
                }
                if (trigger == true) {
                    /* Euclidean selection */
                    double criteriaInsert = this.calEuclideanCriteria(solgap,
                            arc_gap_pair.getRight().getLeft());
                    if (bestCriteria < criteriaInsert) {
                        bestCriteria = criteriaInsert;
                        closestGap = solgap;
                    }
                    if (solgap.end == arc_gap_pair.getRight().getRight().end) break;
                }
            }
            /**
             * To insert the arc in arc_gap_pair (arc_gap_pair.getRight().getLeft())
             * between the closestGap in the solution.
             */
            int starttime_vi_vm = closestGap.actualStarttime;
            Gap best_vi_vm = findTDSP.tdsp(closestGap.start,
                    arc_gap_pair.getRight().getLeft().source,
                    GreedLS.TimeCost2Idx(starttime_vi_vm));
            int starttime_vn_vj_int = starttime_vi_vm + best_vi_vm.SPCost;
            Gap best_vn_vj = findTDSP.tdsp(arc_gap_pair.getRight().getLeft().target,
                    closestGap.end, GreedLS.TimeCost2Idx(starttime_vn_vj_int));

            /* BELOW MODIFIED: to disallow same vertex to be walked through more than once */
            HashSet<Integer> remainedVertices = new HashSet<Integer>();
            for (Gap gap : this.solution.gapList) {
                if (gap != closestGap) {
                    remainedVertices.addAll(gap.vexIDList);
                }
            }
            HashSet<Integer> ViVmSet = new HashSet<Integer>(best_vi_vm.vexIDList);
            HashSet<Integer> VnVjSet = new HashSet<Integer>(best_vn_vj.vexIDList);
            HashSet<Integer> s = new HashSet<Integer>(ViVmSet);
            s.retainAll(VnVjSet);
            ViVmSet.retainAll(remainedVertices);
            VnVjSet.retainAll(remainedVertices);
            if (ViVmSet.size() > 0 || VnVjSet.size() > 0 || s.size() > 0) {
                this.tempCAS.remove(arc_gap_pair.getRight().getLeft());
                continue;
            }
            /* ABOVE MODIFIED */

            Pair<Integer, Integer> cost_value_list_vm_vn =
                    GreedLS.graph.adjList.adjacencyList.get(
                            arc_gap_pair.getRight().getLeft().source).get(
                            arc_gap_pair.getRight().getLeft().target);
            int idx_vm = GreedLS.TimeCost2Idx(starttime_vi_vm + (int) best_vi_vm.SPCost);
            int best_new_value = best_vi_vm.collectedValue +
                    best_vn_vj.collectedValue +
                    cost_value_list_vm_vn.getRight();
            int bestDeltaValue = best_new_value - closestGap.collectedValue;
            int best_new_cost = best_vi_vm.SPCost +
                    best_vn_vj.SPCost +
                    cost_value_list_vm_vn.getLeft();
            int bestDeltaCost = best_new_cost - closestGap.SPCost;

            if ((bestDeltaValue > 0 || bestDeltaCost < 0) &&
                    this.solution.totalCost + bestDeltaCost <= QuerySetting.budgetTime) {
                /**
                 * Insert bestArc between the gap to update the solution.
                 */
                this.solution.insertArc(closestGap,
                        best_vi_vm,
                        best_vn_vj,
                        bestDeltaValue,
                        bestDeltaCost);
                /**
                 * Remove vertices and arcs inserted into solution from V'' and G''.
                 */
                this.removePathFromtempArc(best_vi_vm);
                this.removePathFromtempArc(best_vn_vj);
                this.tempCAS.remove(arc_gap_pair.getRight().getLeft());
            } else {
                this.tempCAS.remove(arc_gap_pair.getRight().getLeft());
            }
        }
        GreedLS.arcSelectionTime += System.currentTimeMillis() - programCurTime;
        this.CAS.clear();
        this.CAS.addAll(this.tempCAS); //--recalcuate
        this.verticeEALD_map.clear();
        this.verticeEALD_map.putAll(this.tempVerticeEALD_map); //--recalcuate

//        this.ArrageSolution();
    }


    /**
     * Iteratively insert promising feasible arcs into the solution path
     * until all the travel time budget is consumed.
     */
    public void GreedLSAlgorithm(int budget) throws Exception {
        String outputFilename = "Graph/output.txt";
        String queryFilestr = "Graph/query.txt";

        FileWriter fw = new FileWriter(outputFilename, true);
        BufferedWriter bw = new BufferedWriter(fw);
        GreedLS.outputWriter = new PrintWriter(bw);
        GreedLS.outputWriter.println("\n#################################################################");

        List<String> querylines = Files.readAllLines(Paths.get(queryFilestr),
                StandardCharsets.UTF_8);
        for (String stpair : querylines) {
            String[] parts = stpair.split(",");
            QuerySetting.SourceVexID = Integer.parseInt(parts[0]);
            QuerySetting.TargetVexID = Integer.parseInt(parts[1]);
            QuerySetting.budgetTime = Integer.parseInt(parts[2]);
            QuerySetting.runingTimeThreshold = Integer.parseInt(parts[3]);

            /* BELOW MODIFIED: to allow source and target to be same vertex */
            if (QuerySetting.SourceVexID == QuerySetting.TargetVexID) {
                int sourceCopyId = graph.vertices.size();
                QuerySetting.TargetVexID = sourceCopyId;

                // this does not actually copy the HashMap but just a reference
                Map<Integer, Pair<Integer, Integer>> sourceCopyAdjMap = graph.adjList.adjacencyList.get(QuerySetting.SourceVexID);
                if (sourceCopyAdjMap != null) {
                    graph.adjList.adjacencyList.put(sourceCopyId, new HashMap<Integer, Pair<Integer, Integer>>());
                    (graph.adjList.adjacencyList.get(sourceCopyId)).putAll(sourceCopyAdjMap);
                }
                Map<Integer, Pair<Integer, Integer>> sourceCopyRevAdjMap = graph.adjList.reverse_adjacencyList.get(QuerySetting.SourceVexID);
                if (sourceCopyRevAdjMap != null) {
                    graph.adjList.reverse_adjacencyList.put(sourceCopyId, new HashMap<Integer, Pair<Integer, Integer>>());
                    (graph.adjList.reverse_adjacencyList.get(sourceCopyId)).putAll(sourceCopyRevAdjMap);
                }

                for (Map.Entry<Integer, Map<Integer, Pair<Integer, Integer>>> entry : graph.adjList.adjacencyList.entrySet()) {
                    // if this vertex point to source, do copy
                    if (entry.getValue().containsKey(QuerySetting.SourceVexID)) {
                        Pair<Integer, Integer> p = entry.getValue().get(QuerySetting.SourceVexID);
                        entry.getValue().put(sourceCopyId, p);
                    }
                }
                for (Map.Entry<Integer, Map<Integer, Pair<Integer, Integer>>> entry : graph.adjList.reverse_adjacencyList.entrySet()) {
                    // if this vertex point to source, do copy
                    if (entry.getValue().containsKey(QuerySetting.SourceVexID)) {
                        Pair<Integer, Integer> p = entry.getValue().get(QuerySetting.SourceVexID);
                        entry.getValue().put(sourceCopyId, p);
                    }
                }

                Vertex sourceVertex = graph.vertices.get(QuerySetting.SourceVexID);
                Vertex sourceVertexCopy = new Vertex(sourceCopyId, sourceVertex.getLat(), sourceVertex.getLng());
                graph.vertices.add(sourceVertexCopy);
            }
            System.out.println(graph.vertices.size() + " vertices in graph.");
            /* ABOVE IS MODIFIED */

            Gap sp = new Gap();
            sp = findTDSP.tdsp(QuerySetting.SourceVexID,
                    QuerySetting.TargetVexID,
                    GreedLS.Idx2TimeCost(QuerySetting.startTime));
            //double budgetDouble = 2.0*sp.SPCost;
            //QuerySetting.budgetTime = (int)budgetDouble;

            GreedLS.optimalValueIdx = -1;
            GreedLS.optimalValueIdx++;


            GreedLS.outputWriter.println("------------------");
            GreedLS.outputWriter.println("Query Setting:\n"
                    + "source: "
                    + QuerySetting.SourceVexID
                    + "\ttarget: "
                    + QuerySetting.TargetVexID
                    + "\tstartTime: "
                    + QuerySetting.startTime
                    + "\tTDSP: "
                    + sp.SPCost
                    + "\tbudget: "
                    + ((sp.SPCost != 0) ? (QuerySetting.budgetTime / sp.SPCost * 100) : "inf")
                    + "% SP"
                    + "\tSPValue: "
                    + sp.collectedValue);

            //---initialize all the data structures / variables
            this.solution.empty();
            GreedLS.iterationNUM = 0;
            GreedLS.curMaxScenicValue = 0 - Integer.MAX_VALUE;
            long runningTime = 0;
            this.copyFromGraph();
            this.initSolution();
            this.programStartTime = System.currentTimeMillis(); //---------start timing
            //---finish initialization

            while (true) {
//                GreedLS.outputWriter.print("Iter: " + iterationNUM + "\t");
                iterationNUM++;

                if (runningTime > QuerySetting.runingTimeThreshold * 1000) {
                    GreedLS.outputWriter.print("Time out.. Terminate search algorithm\n");
                    break; //terminate in 1 second
                }


                GreedLS.calculateCandArcTime = 0;
                GreedLS.arcSelectionTime = 0;

                this.GreedLSAlgorithm_OneIteration();//kernel function

                programCurTime = System.currentTimeMillis();
                runningTime += programCurTime - programStartTime;
                GreedLS.outputWriter.print("TotalProcessTimeSofar: "
                        + runningTime
                        + "\t");
                GreedLS.outputWriter.print("calculateCandArcTime: "
                        + GreedLS.calculateCandArcTime
                        + "\t");
                GreedLS.outputWriter.print("arcSelectionTime: "
                        + GreedLS.arcSelectionTime
                        + "\t");
                this.printSolution();
                GreedLS.outputWriter.flush();
            }//end while true/iteration
            /* BELOW MODIFIED: print output to file */
            LinkedList<Integer> pathList = new LinkedList<Integer>();
            for (Gap gap : this.solution.gapList) {
                pathList.addAll(gap.vexIDList);
            }
            GreedLS.outputWriter.println(pathList);
            /* ABOVE MODIFIED */
        }//end for each queryline
        GreedLS.outputWriter.close();
    }


}
