package greedLS;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Random;


/**
 * @author Ying Lu <ylu720@usc.edu>
 *  
 *
 *
 *           <p>
 *           input: road network in the form of adjacency list.
 * 
 *           AdjList_Thursday.txt is a time-dependent road network with
 *           approximately 47K modes. The format of the file is as follows
 * 
 *           n1(V): 6146,6377,6090, ,7688;n4(V):5917,6139,, 7401;
 *           n0(V):6146,6377,6090, . ,7688;n2(V):6031,6258, n3(V): ; n7(V):.;
 *           n9(V):; Where n0 is connected to n1 and n4 (1st file) n1 is connect
 *           to n0; and n2 (2nd line) n2 is connect to n3; and n7; and n9 ...
 *           ... ... n47K is connected to .... V stands for Variable travel
 *           time. The ideas is the following. Lets assume n8(F):5321 where F
 *           stands for Fixed we would not repeat the cost 5321 for all time
 *           instances to save space.
 *
 *           output: A data structure that resides in memory and contains the
 *           representation of the road network. n1->Dynamic List Builder
 *           {(n2,[60]),(n100,[60]),()...()}
 *
 *           it applies to both static and dynamic lists, depending on the
 *           constructor. statistic function prints statistics for the memory
 *           consumption (remains low, less than 50MB )
 */
public class AdjList {
	public Map<Integer, Map<Integer, Pair<Integer,Integer>>> adjacencyList = 
	       new HashMap<Integer, Map<Integer, Pair<Integer,Integer>>>();
	public Map<Integer, Map<Integer, Pair<Integer,Integer>>> reverse_adjacencyList = 
	       new HashMap<Integer, Map<Integer, Pair<Integer,Integer>>>();
	private int MAX_VALUE = 100;
	/**
	 * Dynamic List Builder
	 * 
	 * @param inFile
	 * @param type
	 *            0 means dynamic list generation
	 */
	public AdjList(String inFile, int type) {
		Random rand = new Random();
		Pair<Integer,Integer> pair;
		System.out.println("Loading Dynamic File: " + inFile);
		try {
			List<String> lines = Files.readAllLines(Paths.get(inFile), StandardCharsets.UTF_8);
			Arc arc;
			System.out.println("Building graph. Please wait...");
			int i = 0;
			for (String temp : lines) {
				if (i % 100 == 0) System.out.print(i * 100 / 1000 + "%...");
				arc = new Arc();
				StringTokenizer sT = new StringTokenizer(temp, ":");
				temp = sT.nextToken();
				arc.source = Integer.parseInt( temp.substring(0, temp.indexOf(',')) );
				arc.target = Integer.parseInt( temp.substring(temp.indexOf(',')+1) );
				
				int cost, value = 0;
				temp = sT.nextToken(); 
				StringTokenizer sT2 = new StringTokenizer(temp, ";");
				int precost = -1, prevalue = -1;
				while(sT2.hasMoreTokens()){
					temp = sT2.nextToken();
					cost = Integer.parseInt(temp.substring(0, temp.indexOf(',')));
					
					/* Obtain value from file calculated from Flickr photos */
					value = Integer.parseInt( temp.substring(temp.indexOf(',')+1) ); 
					//int randvalue = rand.nextInt(this.MAX_VALUE); //---randomly generated
					//System.out.println(arc.source+ ","+ arc.target + ":" + cost + ","+ value);
					arc.cost_value_list=new Pair<Integer,Integer>(cost,value);
				}
				if(this.adjacencyList.get(arc.source) == null){
					this.adjacencyList.put(arc.source, new HashMap<Integer, Pair<Integer,Integer>>());
				}
				(this.adjacencyList.get(arc.source)).put(arc.target, arc.cost_value_list);
				
				if(this.reverse_adjacencyList.get(arc.target)==null){
					this.reverse_adjacencyList.put(arc.target, new HashMap<Integer, Pair<Integer,Integer>>());
				}
				(this.reverse_adjacencyList.get(arc.target)).put(arc.source, arc.cost_value_list);
				
				i++;
			}
		}

		catch (IOException io) {
			System.err.println(io.toString());
			System.exit(1);
		} catch (RuntimeException re) {
			System.err.println(re.toString());
			System.exit(1);
		}
		System.out.println("Finished Loading... ");
	}
	
	


	public Map<Integer, Map<Integer, Pair<Integer,Integer>>> getList() {
		return this.adjacencyList;

	}


	public int getSize() {
		return this.adjacencyList.size();
	}

	public void printMemStat() { // prints statistics of memory consumption for
									// the list data structure
		Runtime runtime = Runtime.getRuntime();

		NumberFormat format = NumberFormat.getInstance();

		StringBuilder sb = new StringBuilder();
		long maxMemory = runtime.maxMemory();
		long allocatedMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		System.out.println(sb);
		sb.append("free memory: " + format.format(freeMemory / 1024) + "kB\n");
		sb.append("allocated memory: " + format.format(allocatedMemory / 1024) + "kB\n");
		sb.append("max memory: " + format.format(maxMemory / 1024) + "kB\n");
		sb.append("total free memory: "
				+ format.format((freeMemory + (maxMemory - allocatedMemory)) / 1024) + "kB\n");

		System.out.println(sb);
	}

	//public static void main(String[] args) {
		// BuildList b = new BuildList();
		// int target=1;
		//
		//
		//
		// List<Pair<Integer, int[]>> neighbors= b.adjacencyList.get(target);
		// System.out.println(target+"->");
		// for (Pair<Integer, int[]>n :neighbors){
		// System.out.print(n.getLeft()+": ");
		// for (int time: n.getRight()){
		// System.out.print(time+" ");
		//
		// }
		// System.out.println();
		// }
		// System.out.println("size="+b.adjacencyList.keySet().size()+"!!");
		// System.out.println("size_correct=46936-30=46906");
	//}

}
