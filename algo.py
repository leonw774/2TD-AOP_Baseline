import os
import subprocess
import networkx as nx


def find_tfvrpath(tfvrnet: nx.Graph, budget: float, running_time_threshold: float, no_build_java: bool):
    """
        input:
        - transformed virtual network
        - budget (cost limit)
        - runing time threshold (in second)
        - no_build_java: if True, will no build java classes before try to run
        return: found path
    """
    BUDGET_MAX = 1073741823
    ######## make input files
    with open('GreedLS/Graph/query.txt', 'w+', encoding='utf8') as query_file:
        if budget == float('inf'):
            budget = BUDGET_MAX # INT32_MAX / 2 - 1
        else:
            budget = min(BUDGET_MAX, int(budget*1e3))
        query_file.write(f'0,0,{budget},{running_time_threshold}\n')

    with open('GreedLS/Graph/Nodes.csv', 'w+', encoding='utf8') as node_file:
        id2node = dict()
        nodes_str = ''
        for n in tfvrnet.nodes():
            nid = tfvrnet.nodes[n]['nid'] = len(id2node)
            id2node[nid] = n
            nodes_str += f'n{nid},{n[0]},{n[1]}\n'
        node_file.write(nodes_str)

    with open('GreedLS/Graph/Arcs.txt', 'w+', encoding='utf8') as arc_file:
        arcs_str = ''
        for u, v in tfvrnet.edges():
            # because they only suppport integer cost and value
            int_weight = int(tfvrnet.edges[u, v]["weight"]*1e3)
            # print(tfvrnet.edges[u, v]["weight"], int_weight)
            # because they use directed graph
            arcs_str += f'{tfvrnet.nodes[u]["nid"]},{tfvrnet.nodes[v]["nid"]}:{int_weight},2\n'
            arcs_str += f'{tfvrnet.nodes[v]["nid"]},{tfvrnet.nodes[u]["nid"]}:{int_weight},2\n'
        arc_file.write(arcs_str)

    ######## build java
    if not no_build_java:
        os.chdir('./GreedLS')
        subprocess.Popen('javac -d classes src/greedLS/*.java').wait()
        os.chdir('..')

    ######## run java program to find best path
    os.chdir('./GreedLS')
    subprocess.Popen('java -classpath classes greedLS.Main').wait()
    os.chdir("..")

    ######## get output
    with open('GreedLS/Graph/output.txt', 'r', encoding='utf8') as output_file:
        path_str = output_file.readlines()[-1]
        try:
            assert path_str[0] != '#'
        except AssertionError as e:
            print('GreedLS cannot find path')
            raise e
    path = [id2node.get(int(x), id2node[0]) for x in path_str[1:-2].split(', ')]
    return path