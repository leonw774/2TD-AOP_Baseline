from argparse import ArgumentParser
from cost import costFunc
from nets import getPhysical, getVirtual, makeTransformedVirtual
import networkx as nx
from out import outputResult
import os
import subprocess


from io import StringIO
import cProfile
import pstats
from time import time

"""
    input: transformed virtual network, runingTimeThreshold (in second)
    return: found path
"""
def findTransformedVirtualPath(tfvrNet: nx.Graph, budget: float, runingTimeThreshold: float):
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
        id = tfvrNet.nodes[n]['id']
        id2node[id] = n
        nodesStr += f'n{id},{n[0]},{n[1]}\n'
    nodes.write(nodesStr)
    nodes.close()

    arcsStr = ''
    for u, v in tfvrNet.edges():
        # because only suppport integer cost and value 
        int_weight = int(tfvrNet.edges[u, v]["weight"]*1e3)
        # because they use directed graph
        arcsStr += f'{tfvrNet.nodes[u]["id"]},{tfvrNet.nodes[v]["id"]}:{int_weight},2\n'
        arcsStr += f'{tfvrNet.nodes[v]["id"]},{tfvrNet.nodes[u]["id"]}:{int_weight},2\n'
    arcs.write(arcsStr)
    arcs.close()

    ######## compile & run java program to find best path
    os.chdir("./GreedLS")
    subprocess.Popen('javac -d classes src/greedLS/*.java').wait()
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

"""
    input: transformed virtual network, virtual network, physical network, found virtual path
    return: virtual path, physical path, totalCost
"""
def getVirtualAndPhysicalPath(tfvrNet: nx.Graph, vrNet: nx.Graph, phNet: nx.Graph, tfvrPath: list):
    vrPath = [tfvrPath[0]]
    for i in range(len(tfvrPath) - 1):
        vrSubPath = tfvrNet.edges[tfvrPath[i], tfvrPath[i+1]]['path']
        # print(f'vrSubPath[{i}]:, {vrSubPath}')
        if vrSubPath[-1] == vrPath[-1]:
            vrSubPath = list(reversed(vrSubPath))
        vrPath.extend(vrSubPath[1:])

    phPath = [vrNet.nodes[vrPath[0]]['phy']]
    for i in range(len(vrPath)-1):
        u, v = vrNet.nodes[vrPath[i]]['phy'], vrNet.nodes[vrPath[i+1]]['phy']
        if phNet.has_edge(u, v):
            phPath.append(v)
        else:
            sp = nx.dijkstra_path(phNet, u, v)
            phPath.extend(sp[1:])

    totalCost = sum(vrNet.edges[vrPath[n], vrPath[n+1]]['cost'] for n in range(len(vrPath) - 1))
    return vrPath, phPath, totalCost


if __name__ == '__main__':
    parser = ArgumentParser()
    parser.add_argument('--virtual-world-file', '-v',
                        dest='virtual_filepath',
                        required=True,
                        type=str
                        )
    parser.add_argument('--physical-world-file', '-p',
                        dest='physical_filepath',
                        required=True,
                        type=str
                        )
    parser.add_argument('--runtime-threshold', '--runtime', 
                        dest='runtime',
                        type=int,
                        default=10,
                        help='runtime limit'
                        )
    parser.add_argument('--budget', '-b',
                        dest='budget',
                        type=float,
                        default=1073741823 # INT32_MAX / 2
                        )
    parser.add_argument('--alpha', '-a',
                        dest='alpha',
                        type=float,
                        default=1.0,
                        help='alpha for transformed virtual network edge weight'
                        )
    parser.add_argument('--output-filepath', '-o',
                        dest='output',
                        type=str,
                        nargs='?',
                        const='output/out',
                        help='enable output'
                        )
    parser.add_argument('--profile',
                        dest='use_profile',
                        action="store_true",
                        help='enable profiling'
                        )
    args = parser.parse_args()

    if args.use_profile:
        pr = cProfile.Profile()
        pr.enable()

    print(f'virtual file:{args.virtual_filepath}')
    print(f'physical file:{args.physical_filepath}')
    
    ######## GET INPUT

    time_getnets = time()

    vrNet, source, destinations = getVirtual(args.virtual_filepath)

    phNet, obstacles, phWorldL, phWorldW = getPhysical(args.physical_filepath)

    tfvrNet = makeTransformedVirtual(vrNet, source, destinations, args.alpha, costFunc)

    print(f'virtual network has {vrNet.number_of_nodes()} nodes and {vrNet.number_of_edges()} edges')
    print(f'alpha: {args.alpha}, source: {source}, destination: {destinations}')
    print(f'physical network has {phNet.number_of_nodes()} nodes and {phNet.number_of_edges()} edges')
    print(f'transformed virtual network has {tfvrNet.number_of_nodes()} nodes and {tfvrNet.number_of_edges()} edges')

    print(f'read and make nets: {time()-time_getnets} seconds')

    print(f'budget: {args.budget}')
    print(f'algorithm parameter: runtimeThreshold={args.runtime}')

    time_findpaths = time()

    ######## FIND VIRTUAL PATH
    tfvrPath = findTransformedVirtualPath(tfvrNet, args.budget, args.runtime)
    print("tfvrPath:", tfvrPath)

    ######## GET CORRESPONDING PHYSICAL PATH
    vrPath, phPath, totalCost = getVirtualAndPhysicalPath(tfvrNet, vrNet, phNet, tfvrPath)
    # print("vrPath:", vrPath)
    # print("phPath:", phPath)
    # print("totalCost:", totalCost)
    if args.budget:
        if totalCost > args.budget:
            print(f'Can not find path: the total cost: {totalCost} is larger than budget: {args.budget}')
            exit()
    
    print(f'find paths: {time()-time_findpaths} seconds')

    ######## OUTPUT

    if args.output:
        outputResult(vrPath, totalCost, vrNet, source, destinations, phPath, phWorldL, phWorldW, obstacles, args)

    if args.use_profile:
        pr.disable()
        s = StringIO()
        pstats.Stats(pr, stream=s).strip_dirs().sort_stats('cumulative').print_stats()
        open('stat', 'w+', encoding='utf8').write(s.getvalue())
