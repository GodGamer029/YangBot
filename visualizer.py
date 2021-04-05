
from pyqtgraph.Qt import QtGui, QtCore
import numpy as np
import code
import pyqtgraph as pg

import pyqtgraph.examples
from numpy.polynomial.polynomial import Polynomial as Poly
#pyqtgraph.examples.run()

#QtGui.QApplication.setGraphicsSystem('raster')
app = QtGui.QApplication([])
#mw = QtGui.QMainWindow()
#mw.resize(800,800)
np.set_printoptions(suppress=True, precision=2, linewidth=200)


win = pg.GraphicsWindow(title="Basic plotting examples")
win.resize(1000,600)
win.setWindowTitle('pyqtgraph example: Plotting')

# Enable antialiasing for prettier plots
pg.setConfigOptions(antialias=True)

#p1 = win.addPlot(title="Basic array plotting", y=np.genfromtxt('yeet.dat'))

p3 = win.addPlot(title="Drawing with points")

names = ["distance", "startspeed", "boost"]
def loadSet(fName, rowUse):

    driftyBoi = np.genfromtxt(fName, skip_header=1)
    print(driftyBoi.shape)
    allRows = np.arange(0, driftyBoi.shape[1])
    skippedRows, cunt = np.unique(np.append(allRows, rowUse), return_counts=True)

    skippedRows = skippedRows[cunt == 1]
    skippedRows = sorted(skippedRows)[::-1]

    for row in skippedRows:
        driftyBoi = np.delete(driftyBoi, row, 1)

    return driftyBoi[20:-5]

def polyDataFor(arr):
    ''' t -> vel fit
    func = Poly.fit(arr[:, 0], arr[:, 1], 1)
    data = np.empty_like(arr)
    for i in range(len(arr)):
        data[i] = [arr[i,0], func(arr[i,0])]'''

    # before -> vel fit
    func = Poly.fit(arr[:-1, 1], (arr[1:, 1] - arr[:-1, 1]) / (arr[1:, 0] - arr[:-1, 0]), 1, domain=[])
    print("Poly fitted:", func, func.domain, func.window)
    data = np.empty((arr.shape[0] - 1, arr.shape[1]))
    prevV = arr[0, 1]
    for i in range(len(arr) - 1):
        prevT = arr[i, 0]
        nextT = arr[i+1, 0]
        #nextV = prevV + func(prevV) * (nextT - prevT)
        #fac = - 0.0305 * prevV # free in air -0.4690 
        #fac = -227.20539704480407 - 0.03047 * prevV # rollin on ground > 550 vel
        #fac = func(prevV)
        nextV = prevV + fac * (nextT - prevT)
        data[i] = [nextT, nextV] #func(prevV)
        prevV = nextV
    return data

rows = [0, 1]

data = loadSet('data/yeet-000.csv', [2])
print(data)
y,x = np.histogram(data, bins=np.linspace(np.min(data), np.max(data), 100))
curve = pg.PlotCurveItem(x, y, stepMode=True, fillLevel=0, brush=(0, 0, 255, 80))

p3.addItem(curve)
#datasets = []
#for i in range(5):
#    datasets.append(loadSet('data/turnerror-00{}.csv'.format(i), rows))
#bolPoly = polyDataFor(bol)

#for data in datasets:
#    p3.plot(data, pen='g', symbolBrush=None, symbolPen=None)
#p3.plot(bolPoly, pen='w', symbolBrush=None, symbolPen=None)

p3.setLabel('left', "")
p3.setLabel('bottom', "distance")

def getDude(start):
    return (driftyBoi[start+1,1] - driftyBoi[start, 1]) / (driftyBoi[start, 1] * (driftyBoi[start + 1, 0] - driftyBoi[start, 0]))

## Start Qt event loop unless running in interactive mode or using pyside.
if __name__ == '__main__':
    import sys
    if (sys.flags.interactive != 1) or not hasattr(QtCore, 'PYQT_VERSION'):
        QtGui.QApplication.instance().exec_()

#code.interact(local=locals())