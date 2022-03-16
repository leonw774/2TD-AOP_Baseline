import networkx as nx
import os
import subprocess

"""
    input: transformed virtual network, runingTimeThreshold (in second)
    return: found path
"""
def findTransformedVirtualPath(tfvrNet: nx.Graph, budget: float, runingTimeThreshold: float, noBuildJava: bool):
    ######## make input files
    query = open('GreedLS/Graph/query.txt', 'w+', encoding='utf8')
    nodes = open('GreedLS/Graph/Nodes.csv', 'w+', encoding='utf8')
    arcs = open('GreedLS/Graph/Arcs.txt', 'w+', encoding='utf8')
    # clear output.txt
    output = open('GreedLS/Graph/output.txt', 'w+', encoding='utf8')
    output.close()

    query.write(f'0,0,{budget},{runingTimeThreshold}\n')
    query.close()

    id2node = dict()
    nodesStr = ''
    for n in tfvrNet.nodes():
        id = tfvrNet.nodes[n]['nid'] = len(id2node)
        id2node[id] = n
        nodesStr += f'n{id},{n[0]},{n[1]}\n'
    nodes.write(nodesStr)
    nodes.close()

    arcsStr = ''
    for u, v in tfvrNet.edges():
        # because they only suppport integer cost and value
        int_weight = int(tfvrNet.edges[u, v]["weight"]*1e3)
        # print(tfvrNet.edges[u, v]["weight"], int_weight)
        # because they use directed graph
        arcsStr += f'{tfvrNet.nodes[u]["nid"]},{tfvrNet.nodes[v]["nid"]}:{int_weight},2\n'
        arcsStr += f'{tfvrNet.nodes[v]["nid"]},{tfvrNet.nodes[u]["nid"]}:{int_weight},2\n'
    arcs.write(arcsStr)
    arcs.close()

    ######## build java
    if not noBuildJava:
        os.chdir('./GreedLS')
        subprocess.Popen('javac -d classes src/greedLS/*.java').wait()
        os.chdir('..')

    ######## run java program to find best path
    os.chdir('./GreedLS')
    subprocess.Popen('java -classpath classes greedLS.Main').wait()
    os.chdir("..")

    ######## get output
    output = open('GreedLS/Graph/output.txt', 'r', encoding='utf8')
    pathStr = output.readlines()[-1]
    if pathStr[0] == '#':
        print('findTransformedVirtualPath: cannot find path')
        exit()
    path = [id2node.get(int(x), id2node[0]) for x in pathStr[1:-2].split(', ')]
    return path