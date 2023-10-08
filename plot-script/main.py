import matplotlib.pyplot as plt


def plot_comparison():
    # Sample data
    loads = ['10x10', '10x20', '10x30']
    java_throughput = [604.63, 600.75, 605.89]
    go_throughput = [594.06, 597.64, 593.27]

    bar_width = 0.25
    index = range(len(loads))

    fig, ax = plt.subplots()
    bar1 = ax.bar(index, java_throughput, bar_width, label='Java', color='b')
    bar2 = ax.bar([i + bar_width for i in index], go_throughput, bar_width, label='Go', color='r')

    ax.set_xlabel('Load')
    ax.set_ylabel('Throughput (Requests/sec)')
    ax.set_title('Throughput comparison between Java and Go in Client1')
    ax.set_xticks([i + bar_width / 2 for i in index])
    ax.set_xticklabels(loads)
    ax.legend(loc='upper left', bbox_to_anchor=(1, 1))

    for bar in bar1:
        yval = bar.get_height()
        ax.text(bar.get_x() + bar.get_width() / 2, yval + 5, round(yval, 2), ha='center', va='bottom', fontsize=8)

    for bar in bar2:
        yval = bar.get_height()
        ax.text(bar.get_x() + bar.get_width() / 2, yval + 5, round(yval, 2), ha='center', va='bottom', fontsize=8)

    plt.tight_layout()
    plt.show()


def print_hi(name):
    print(f'Hi, {name}')


if __name__ == '__main__':
    print_hi('PyCharm')
    plot_comparison()
