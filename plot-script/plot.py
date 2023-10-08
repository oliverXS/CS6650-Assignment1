import pandas as pd
import matplotlib.pyplot as plt

# Read the CSV data
df = pd.read_csv('csv_api_record_Java_30_10.csv')

# Convert Start Time to seconds from the beginning
df['Start Time'] = (df['Start Time'] - df['Start Time'].min()) // 1000

# Group by the second and count the number of requests
throughput = df.groupby('Start Time').size()

# Plotting
plt.plot(throughput.index, throughput.values)
plt.xlabel('Time (seconds)')
plt.ylabel('Requests/Second')
plt.title('Throughput over Time by Java')
plt.grid(True)
plt.show()
