import networkx as nx
import matplotlib.pyplot as plt
import json


def outputJSON(vrPath: list, totalCost: float, totalLength: float, vrNet: nx.Graph, source, destinations, args):
    resultObj = {
        'Steps': [],
        'Total cost': totalCost,
        'Total length': totalLength
    }

    for i in range(len(vrPath) - 1):
        u, v = vrPath[i], vrPath[i+1]
        stepObj = {
            'left': vrNet.nodes[u]['nid'],
            'right': vrNet.nodes[v]['nid'],
            'length': vrNet.edges[u, v]['length'],
            'cost': vrNet.edges[u, v]['cost']
        }
        # print(stepObj)
        resultObj['Steps'].append(stepObj)
    json.dump(resultObj, open(f'{args.output}_path.json', 'w+', encoding='utf8'))


def outputImage(
    vrPath: list, totalCost: float, totalLength: float, vrNet: nx.Graph, source, destinations,
    phPath:list, phWorldL: int, phWorldW: int, obs: list, args):

    plt.figure(figsize=(12, 6))

    plt.subplot(1, 2, 1)
    plt.axis('off')
    ######## DRAW VIRTUAL WORLD

    # draw all edges
    # darker means bigger score
    # max_score = max([attr.get('score', 0) for u, v, attr in vrNet.edges(data=True)])
    # for u, v, attr in vrNet.edges(data=True):
    #     d = attr.get('score', 0) / max_score
    #     color = (0.0, 0.0, 0.0, d)
    #     plt.plot(
    #         [u[0], v[0]],
    #         [u[1], v[1]],
    #         '-',
    #         color=color,
    #         lw=1)

    # draw path
    prex = prey = None
    for n in vrPath:
        x, y = n
        if prex is not None:
            plt.plot([prex, x], [prey, y], 'r-', lw=1)
        prex, prey = x, y

    # draw node
    for n in vrNet.nodes():
        if n not in vrPath:
            plt.plot(n[0], n[1], 'gs', markersize=2)
    
    # draw path node
    for i, n in enumerate(vrPath):
        angle = ''
        # if i >= 1 and i < len(vr_path) - 1:
        #     angle = computeAngle(
        #         vr_path[i-1],
        #         vr_path[i],
        #         vr_path[i+1])
        #     angle = '\n' + str(round(degrees(angle), 1))
        if n in destinations or n == source:
            plt.plot(n[0], n[1], 'rs', markersize=2)
        else:
            plt.plot(n[0], n[1], 'bs', markersize=2)
        plt.annotate(
            f'{i}{angle}', 
            (n[0], n[1]+i/(len(phPath))*0.5),
            color='k', 
            fontsize=8)
    
    plt.figtext(
        0.5, 0.99,
        f'virtual world: {args.virtual_filepath}\nsource: {source}, destinations: {destinations}\nruntime: {args.runtime}',
        wrap=True,
        horizontalalignment='center',
        verticalalignment='top',
        fontsize=12)

    plt.subplot(1, 2, 2)
    plt.axis('off')
    ######## DRAW PHYSICAL WORLD

    # draw boundry
    plt.plot([-1, phWorldL], [-1, -1], 'k-', lw=1)
    plt.plot([phWorldL, phWorldL], [-1, phWorldW], 'k-', lw=1)
    plt.plot([-1, -1], [-1, phWorldW], 'k-', lw=1)
    plt.plot([-1, phWorldL], [phWorldW, phWorldW], 'k-', lw=1)

    # draw obs
    for i in range(phWorldL):
        for j in range(phWorldW):
            if (i, j) in obs:
                plt.plot(i, j, 'ks', markersize=4)
            else:
                plt.plot(i, j, 'gs', markersize=2)

    if phPath is not None:
        # draw path
        prex = prey = None
        for i, n in enumerate(phPath):
            x, y = n
            plt.annotate(
                str(i), 
                (n[0], n[1]+i/(len(phPath))*0.5),
                color='k', 
                fontsize=10)
            if prex is not None:
                plt.plot([prex, x], [prey, y], 'r-', lw=1)
            prex, prey = x, y

    plt.figtext(
        0.5, 0.1,
        f'physical world: {args.physical_filepath}\ncost: {totalCost} length: {totalLength} budget: {args.budget}',
        wrap=True,
        horizontalalignment='center',
        verticalalignment='top',
        fontsize=12)

    plt.savefig(f'{args.output}_img.png')