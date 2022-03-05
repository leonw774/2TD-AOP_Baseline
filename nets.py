import networkx as nx
from numpy import short
import pandas as pd
from math import dist, sqrt
from random import choice, choices, randint

def getPhysical(path: str) -> nx.Graph:
    with open(path, 'r', encoding='utf-8') as f:
        c = f.readlines()
    assert c[0] == 'obs\n'
    obs = set()
    i = 1
    while c[i] != 'pois\n':
        x, y = c[i].split()
        obs.add((int(x), int(y)))
        i += 1
    # now i point to 'pois'
    assert c[i+1] == 'length\n' and c[i+3] == 'width\n'
    length = int(c[i+2])
    width = int(c[i+4])

    # add all integer index coordinate as node except obs
    P = nx.Graph()
    P.add_nodes_from([ 
                (i, j)
            for j in range(width)
        for i in range(length)
        if (i, j) not in obs
    ])

    # find all neighbors:
    # for any grid, if you can go to another grid in chess queen moves, then that grid is a neighbor
    direction = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]
    for ni in P.nodes():
        for d in direction:
            nj = ni[0] + d[0], ni[1] + d[1]
            while 0 <= nj[0] < length and 0 <= nj[1] < width and nj not in obs:
                P.add_edge(ni, nj, weight=dist(ni, nj))
                nj = nj[0] + d[0], nj[1] + d[1]

    return P, obs, length, width


"""
    A node is a two-integer tuple of (x, y)
"""
def getVirtual(path: str, vpmap_path: str) -> nx.Graph:
    G = nx.Graph()
    with open(path, 'r', encoding='utf-8') as f:
        ls = f.read().split('---\n')[1:]
    node_list = []
    for e in ls:
        n1, n2, v = e.split('\n')[:3]
        n1 = tuple(map(int, n1.split()))
        n2 = tuple(map(int, n2.split()))
        if n1 not in node_list:
            node_list.append(n1)
        if n2 not in node_list:
            node_list.append(n2)
        G.add_node(n1, x=n1[0], y=n1[1])
        G.add_node(n2, x=n2[0], y=n2[1])
        v = v.split()
        G.add_edge(n1, n2, length=sqrt(int(v[0]) ** 2 + int(v[1]) ** 2))

    with open(vpmap_path, 'r', encoding='utf-8') as f:
        ls = f.readlines()[1:-1]
    for i, l in enumerate(ls):
        phy = tuple(map(lambda x: ((float(x) - 2.5) // 5), l.split()))
        G.nodes[node_list[i]]['phy'] = phy

    # keep only the largest connected component
    largest_cc = max(nx.connected_components(G), key=len)
    G = G.subgraph(largest_cc)

    # random generate source and destinations for testing
    source = choice(list(G.nodes()))
    destinations = set(choices(list(G.nodes()), k=randint(6, 10)))

    return G, source, destinations

    
"""
    make transformed virtual network
"""
def makeTransformedVirtual(vrNet: nx.Graph, source: tuple, destnations: set, alpha: float, costFunc: callable) -> nx.Graph:
    # initialize transformd virtual network
    print('making transformed virtual network')
    nodes_of_interest = destnations.union([source])
    tfvrNet = nx.complete_graph(nodes_of_interest)
    
    # calc single edge weights on vrNet
    for u, v in vrNet.edges():
        cost = costFunc(u, v, vrNet.nodes[u]['phy'], vrNet.nodes[v]['phy'])
        vrNet.edges[u, v]['cost'] = cost
        vrNet.edges[u, v]['weight'] = alpha * vrNet.edges[u, v]['length'] + (1 - alpha) * cost
    
    # calc weights for tfvrNet
    edges_to_remove = []
    for u, v in tfvrNet.edges():
        try:
            tfvrNet.edges[u, v]['weight'] = nx.shortest_path_length(vrNet, u, v)
            tfvrNet.edges[u, v]['path'] = nx.shortest_path(vrNet, u, v)
        except nx.exception.NetworkXNoPath:
            edges_to_remove.append((u, v))
    
    for etr in edges_to_remove:
        tfvrNet.remove_edge(*etr)
        

    # add id to tfvrNet nodes: source is 0
    id = 1
    for n in tfvrNet.nodes():
        if n == source:
            tfvrNet.nodes[n]['id'] = 0
        else:
            tfvrNet.nodes[n]['id'] = id
            id += 1

    return tfvrNet