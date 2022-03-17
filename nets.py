import json
import networkx as nx

def get_phnet(path: str) -> nx.Graph:
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
    phnet = nx.Graph()
    phnet.add_nodes_from([ 
                (i, j)
            for j in range(width)
        for i in range(length)
        if (i, j) not in obs
    ])

    # find all neighbors:
    # for any grid, if you can go to another grid in chess queen moves, then that grid is a neighbor
    direction = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]
    for ni in phnet.nodes():
        for d in direction:
            nj = ni[0] + d[0], ni[1] + d[1]
            while 0 <= nj[0] < length and 0 <= nj[1] < width and nj not in obs:
                phnet.add_edge(ni, nj)
                nj = nj[0] + d[0], nj[1] + d[1]

    return phnet, obs, length, width


def get_vrnet(path: str, vpmap_path: str) -> nx.Graph:
    vrnet = nx.Graph()
    with open(path, 'r', encoding='utf-8') as f:
        ls = f.read().split('---\n')[1:]
    node_list = []
    node_set = set()
    for e in ls:
        n1, n2 = e.split('\n')[:2]
        n1 = tuple(map(int, n1.split()))
        n2 = tuple(map(int, n2.split()))
        if n1 not in node_set:
            vrnet.add_node(n1, nid=len(node_list))
            node_list.append(n1)
            node_set.add(n1)
        if n2 not in node_set:
            vrnet.add_node(n2, nid=len(node_list))
            node_list.append(n2)
            node_set.add(n2)
        if not vrnet.has_edge(n1, n2):
            vrnet.add_edge(n1, n2)

    tophy = json.load(open(vpmap_path, 'r', encoding='utf-8'))
    assert tophy['Number of vertex'] == vrnet.number_of_nodes()

    for i, n in enumerate(node_list):
        if str(i) in tophy['Vertex physical positions']:
            m = tophy['Vertex physical positions'][str(i)]
            vrnet.nodes[n]['phy'] = (m['x'] // 5, m['y'] // 5)
        else:
            vrnet.nodes[n]['phy'] = None

    for edge_obj in tophy['Edges'].values():
        u, v = node_list[edge_obj['left']], node_list[edge_obj['right']]
        assert vrnet.has_edge(u, v)
        vrnet.edges[u, v]['length'] = edge_obj['length']
        vrnet.edges[u, v]['cost'] = edge_obj['cost']

    # keep only the largest connected component
    largest_cc = max(nx.connected_components(vrnet), key=len)
    vrnet = vrnet.subgraph(largest_cc)

    # random generate source and destinations for testing
    source = node_list[tophy['Start index']]
    destinations = {node_list[d] for d in tophy['Destination index'].values()}

    return vrnet, source, destinations


def make_tfvrnet(vrnet: nx.Graph, source: tuple, destnations: set, alpha: float) -> nx.Graph:
    """
        input: virtual network, source vertex, destination vertices, alpha
        return: transformed virtual network
    """
    # initialize transformd virtual network
    print('making transformed virtual network')
    nodes_of_interest = destnations.union([source])
    tfvrnet = nx.complete_graph(nodes_of_interest)

    # calc single edge weights on vrnet
    for u, v, attr in vrnet.edges(data=True):
        vrnet.edges[u, v]['weight'] = alpha * attr['length'] + (1 - alpha) * attr['cost']

    # calc weights for tfvrnet
    edges_to_remove = []
    for u, v in tfvrnet.edges():
        try:
            tfvrnet.edges[u, v]['weight'] = nx.shortest_path_length(vrnet, u, v, weight='weight')
            tfvrnet.edges[u, v]['path'] = nx.shortest_path(vrnet, u, v, weight='weight')
        except nx.NetworkXNoPath:
            edges_to_remove.append((u, v))

    for etr in edges_to_remove:
        tfvrnet.remove_edge(*etr)

    return tfvrnet
