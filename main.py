from algo import findTransformedVirtualPath
from argparse import ArgumentParser
from nets import getPhysical, getVirtual, makeTransformedVirtual
import networkx as nx
from out import outputImage, outputJSON
import os
import subprocess

from io import StringIO
import cProfile
import pstats
from time import time


"""
    input: transformed virtual network, virtual network, physical network, found virtual path, source node
    return: virtual path, physical path, totalCost, totalLength
"""
def getPaths(tfvrNet: nx.Graph, vrNet: nx.Graph, phNet: nx.Graph, tfvrPath: list, source):
    # rotate tfvrPath so that it is a circle with the source as first element
    n = tfvrPath.index(source)
    if tfvrPath[0] == tfvrPath[-1]:
        # if vrPath is already a circle
        n = tfvrPath.index(source)
        tfvrPath = tfvrPath[n:-1] + tfvrPath[:n] + [source]
    else:
        tfvrPath = tfvrPath[n:] + tfvrPath[:n] + [source]
    
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
            try:
                sp = nx.dijkstra_path(phNet, u, v)
            except nx.NetworkXNoPath as e:
                print(f'getVirtualAndPhysicalPath: Can not find phyiscal path from {u} to {v}')
                raise e 
            phPath.extend(sp[1:])

    totalCost = sum(vrNet.edges[vrPath[n], vrPath[n+1]]['cost'] for n in range(len(vrPath) - 1))
    totalLength = sum(vrNet.edges[vrPath[n], vrPath[n+1]]['length'] for n in range(len(vrPath) - 1))
    return tfvrPath, vrPath, phPath, totalCost, totalLength


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
    parser.add_argument('--virtual-physical-mapping-file', '-m',
                        dest='vp_mapping_filepath',
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
                        default=1073741823 # INT32_MAX / 2 - 1
                        )
    parser.add_argument('--alpha', '-a',
                        dest='alpha',
                        type=float,
                        default=1.0,
                        help='alpha for transformed virtual network edge weight'
                        )
    parser.add_argument('--no-build-java', '-n',
                        dest='no_build_java',
                        action='store_true',
                        help='if set, program will not compile java code before trying to run it'
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

    vrNet, source, destinations = getVirtual(args.virtual_filepath, args.vp_mapping_filepath)

    phNet, obstacles, phWorldL, phWorldW = getPhysical(args.physical_filepath)

    tfvrNet = makeTransformedVirtual(vrNet, source, destinations, args.alpha)

    print(f'virtual network has {vrNet.number_of_nodes()} nodes and {vrNet.number_of_edges()} edges')
    print(f'alpha: {args.alpha}, source: {source}, destination: {destinations}')
    print(f'physical network has {phNet.number_of_nodes()} nodes and {phNet.number_of_edges()} edges')
    print(f'transformed virtual network has {tfvrNet.number_of_nodes()} nodes and {tfvrNet.number_of_edges()} edges')

    print(f'read and make nets: {time()-time_getnets} seconds')

    print(f'budget: {args.budget}')
    print(f'algorithm parameter: runtimeThreshold={args.runtime}')

    time_findpaths = time()

    ######## FIND VIRTUAL PATH

    tfvrPath = findTransformedVirtualPath(tfvrNet, args.budget, args.runtime, args.no_build_java)
    try:
        assert set(tfvrPath) == destinations | {source}
    except AssertionError as e:
        print('Error: tfvrPath does not contain all destinations or source. Missing:', (destinations | {source}) - set(tfvrPath))
        exit()

    ######## GET CORRESPONDING PHYSICAL PATH

    tfvrPath, vrPath, phPath, totalCost, totalLength = getPaths(tfvrNet, vrNet, phNet, tfvrPath, source)
    print(f'tfvrPath: {tfvrPath}')
    # print(f'vrPath: {vrPath}')
    # print(f'phPath: {phPath}')
    print(f'totalCost: {totalCost}, totalLength: {totalLength}')
    if args.budget:
        if totalCost > args.budget:
            print(f'Can not find path: total cost: {totalCost} is larger than budget: {args.budget}')
            exit()
    
    print(f'find paths: {time()-time_findpaths} seconds')

    ######## OUTPUT

    if args.output:
        outputJSON(vrPath, totalCost, totalLength, vrNet, source, destinations, args)
        outputImage(vrPath, totalCost, totalLength, vrNet, source, destinations, phPath, phWorldL, phWorldW, obstacles, args)

    if args.use_profile:
        pr.disable()
        s = StringIO()
        pstats.Stats(pr, stream=s).strip_dirs().sort_stats('cumulative').print_stats()
        open('stat', 'w+', encoding='utf8').write(s.getvalue())
