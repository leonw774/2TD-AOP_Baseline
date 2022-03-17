import json
import networkx as nx
import matplotlib.pyplot as plt


def output_json(vrpath: list, total_cost: float, total_length: float, vrnet: nx.Graph, args):
    result_obj = {
        'Steps': [],
        'Total cost': total_cost,
        'Total length': total_length
    }

    for i in range(len(vrpath) - 1):
        u, v = vrpath[i], vrpath[i+1]
        step_obj = {
            'left': vrnet.nodes[u]['nid'],
            'right': vrnet.nodes[v]['nid'],
            'length': vrnet.edges[u, v]['length'],
            'cost': vrnet.edges[u, v]['cost']
        }
        # print(step_obj)
        result_obj['Steps'].append(step_obj)
    json.dump(result_obj, open(f'{args.output}_path.json', 'w+', encoding='utf8'))


def output_image(
        vrpath: list, total_cost: float, total_length: float, vrnet: nx.Graph, source, destinations,
        phpath:list, ph_world_info:tuple, args):

    obs, ph_l, ph_w = ph_world_info

    plt.figure(figsize=(12, 6))

    ######## DRAW VIRTUAL WORLD

    plt.subplot(1, 2, 1)
    plt.axis('off')

    # draw path
    prex = prey = None
    for n in vrpath:
        x, y = n
        if prex is not None:
            plt.plot([prex, x], [prey, y], 'r-', lw=1)
        prex, prey = x, y

    # draw node
    for n in vrnet.nodes():
        if n not in vrpath:
            plt.plot(n[0], n[1], 'gs', markersize=2)

    # draw path node
    for i, n in enumerate(vrpath):
        angle = ''
        if n in destinations or n == source:
            plt.plot(n[0], n[1], 'rs', markersize=2)
        else:
            plt.plot(n[0], n[1], 'bs', markersize=2)
        plt.annotate(
            f'{i}{angle}',
            (n[0], n[1]+i/(len(phpath))*0.5),
            color='k',
            fontsize=8)

    plt.figtext(
        0.5, 0.99,
        f'virtual world: {args.virtual_filepath}\n'
            + f'source: {source}, destinations: {destinations}\n'
            + f'sn: {args.sn} itmax: {args.vritmax}',
        wrap=True,
        horizontalalignment='center',
        verticalalignment='top',
        fontsize=12)

    ######## DRAW PHYSICAL WORLD

    plt.subplot(1, 2, 2)
    plt.axis('off')

    # draw boundry
    plt.plot([-1, ph_l], [-1, -1], 'k-', lw=1)
    plt.plot([ph_l, ph_l], [-1, ph_w], 'k-', lw=1)
    plt.plot([-1, -1], [-1, ph_w], 'k-', lw=1)
    plt.plot([-1, ph_l], [ph_w, ph_w], 'k-', lw=1)

    # draw obs
    for i in range(ph_l):
        for j in range(ph_w):
            if (i, j) in obs:
                plt.plot(i, j, 'ks', markersize=4)
            else:
                plt.plot(i, j, 'gs', markersize=2)

    if phpath is not None:
        # draw path
        prex = prey = None
        for i, n in enumerate(phpath):
            x, y = n
            plt.annotate(
                str(i),
                (n[0], n[1]+i/(len(phpath))*0.5),
                color='k',
                fontsize=10)
            if prex is not None:
                plt.plot([prex, x], [prey, y], 'r-', lw=1)
            prex, prey = x, y

    plt.figtext(
        0.5, 0.1,
        f'physical world: {args.physical_filepath} alpha: {args.alpha}\n'
            + f'cost: {total_cost} length: {total_length} cost limit: {args.cost_limit}',
        wrap=True,
        horizontalalignment='center',
        verticalalignment='top',
        fontsize=12)

    plt.savefig(f'{args.output}_img.png')