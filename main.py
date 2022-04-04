from argparse import ArgumentParser
import cProfile
from io import StringIO
import pstats
from time import time
import networkx as nx

from algo import find_tfvrpath
from nets import get_phnet, get_vrnet, make_tfvrnet
from out import output_image, output_json


def get_paths(tfvrnet: nx.Graph, vrnet: nx.Graph, phnet: nx.Graph, tfvrpath: list, source):
    """
        input: transformed virtual network, virtual network, physical network, found virtual path, source node
        return: virtual path, physical path, total_cost, total_length
    """
    # rotate tfvrpath so that it is a circle with the source as first element
    n = tfvrpath.index(source)
    if tfvrpath[0] == tfvrpath[-1]:
        # if vrpath is already a circle
        n = tfvrpath.index(source)
        tfvrpath = tfvrpath[n:-1] + tfvrpath[:n] + [source]
    else:
        tfvrpath = tfvrpath[n:] + tfvrpath[:n] + [source]

    vrpath = [tfvrpath[0]]
    for i in range(len(tfvrpath) - 1):
        vrsubpath = tfvrnet.edges[tfvrpath[i], tfvrpath[i+1]]['path']
        # print(f'vrsubpath[{i}]:, {vrsubpath}')
        if vrsubpath[-1] == vrpath[-1]:
            vrsubpath = list(reversed(vrsubpath))
        vrpath.extend(vrsubpath[1:])

    phpath = [vrnet.nodes[vrpath[0]]['phy']]
    for i in range(len(vrpath)-1):
        u, v = vrnet.nodes[vrpath[i]]['phy'], vrnet.nodes[vrpath[i+1]]['phy']
        if phnet.has_edge(u, v):
            phpath.append(v)
        else:
            try:
                sp = nx.dijkstra_path(phnet, u, v)
            except nx.NetworkXNoPath as nopatherror:
                print(f'Can not find phyiscal path from {u} to {v}')
                raise nopatherror 
            phpath.extend(sp[1:])

    total_cost = sum(vrnet.edges[vrpath[n], vrpath[n+1]]['cost'] for n in range(len(vrpath) - 1))
    total_length = sum(vrnet.edges[vrpath[n], vrpath[n+1]]['length'] for n in range(len(vrpath) - 1))
    return tfvrpath, vrpath, phpath, total_cost, total_length


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
                        dest='runtime_threshold',
                        type=int,
                        default=10
                        )
    parser.add_argument('--cost-limit', '-c',
                        dest='cost_limit',
                        type=float,
                        default=float('inf')
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

    vrnet, source, destinations = get_vrnet(args.virtual_filepath, args.vp_mapping_filepath)

    phnet, obstacles, ph_l, ph_w = get_phnet(args.physical_filepath)
    ph_world_info = (obstacles, ph_l, ph_w)

    tfvrnet = make_tfvrnet(vrnet, source, destinations, args.alpha)

    print(f'virtual network has {vrnet.number_of_nodes()} nodes and {vrnet.number_of_edges()} edges')
    print(f'alpha: {args.alpha}, source: {source}, destination: {destinations}')
    print(f'physical network has {phnet.number_of_nodes()} nodes and {phnet.number_of_edges()} edges')
    print(f'transformed virtual network has {tfvrnet.number_of_nodes()} nodes and {tfvrnet.number_of_edges()} edges')

    print(f'read and make nets: {time()-time_getnets} seconds')

    print(f'physical path cost limit: {args.cost_limit}')
    print(f'algorithm parameter: runtimeThreshold={args.runtime_threshold}')

    time_findpaths = time()

    ######## FIND VIRTUAL PATH

    tfvrpath = find_tfvrpath(tfvrnet, args.cost_limit, args.runtime_threshold, args.no_build_java)
    try:
        assert set(tfvrpath) == destinations | {source}
    except AssertionError as e:
        print('Error: tfvrpath does not contain all destinations or source. Missing:',
              (destinations | {source}) - set(tfvrpath))
        exit()

    ######## GET CORRESPONDING PHYSICAL PATH

    tfvrpath, vrpath, phpath, total_cost, total_length = get_paths(tfvrnet, vrnet, phnet, tfvrpath, source)
    print(f'tfvrpath: {tfvrpath}')
    # print(f'vrpath: {vrpath}')
    # print(f'phpath: {phpath}')
    print(f'total cost: {total_cost}, total length: {total_length}')
    if args.cost_limit:
        if total_cost > args.cost_limit:
            print(f'Can not find path: total cost: {total_cost} is larger than cost limit: {args.cost_limit}')
            exit()

    print(f'find paths: {time()-time_findpaths} seconds')

    ######## OUTPUT

    if args.output:
        output_json(vrpath, total_cost, total_length, vrnet, args)
        output_image(vrpath, total_cost, total_length, vrnet, source, destinations, phpath, ph_world_info, args)

    if args.use_profile:
        pr.disable()
        s = StringIO()
        pstats.Stats(pr, stream=s).strip_dirs().sort_stats('cumulative').print_stats()
        open('stat', 'w+', encoding='utf8').write(s.getvalue())
