
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
np.set_printoptions(suppress=True, precision=5, linewidth=200)

win = pg.GraphicsWindow(title="Basic plotting examples")
win.resize(1000,600)
win.setWindowTitle('pyqtgraph example: Plotting')

# Enable antialiasing for prettier plots
pg.setConfigOptions(antialias=True)

#p1 = win.addPlot(title="Basic array plotting", y=np.genfromtxt('yeet.dat'))

names = ["ang", "speed", "accel", "time", "rise","maxcurv", "steer"]
def loadSet(fName, rowUse):

    driftyBoi = np.genfromtxt(fName, skip_header=1)
    print(driftyBoi.shape)
    allRows = np.arange(0, driftyBoi.shape[1])
    skippedRows, cunt = np.unique(np.append(allRows, rowUse), return_counts=True)

    skippedRows = skippedRows[cunt == 1]
    skippedRows = sorted(skippedRows)[::-1]

    for row in skippedRows:
        driftyBoi = np.delete(driftyBoi, row, 1)

    return driftyBoi[1:-1]

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



p3 = win.addPlot(title="")
def doPlot(name, pen):

    maxspeed = loadSet(name, [0])
    dist = loadSet(name, [1])
    its = loadSet(name, [2])
    ts = loadSet(name, [3])
    v0 = loadSet(name, [4])

    p3.plot(np.concatenate((-dist, maxspeed), axis=1), pen=pen, symbolBrush=None, symbolPen=None)
    p3.plot(np.concatenate((-dist, ts), axis=1), pen=pen, symbolBrush=None, symbolPen=None)
    p3.plot(np.concatenate((-dist, its), axis=1), pen=None, symbolBrush=pen, symbolPen=None)
    p3.plot(np.concatenate((-dist, v0), axis=1), pen=None, symbolBrush=None, symbolPen=pen)


#doPlot('data/6093dot3374lol.csv', 'w')
#doPlot('data/5094dot484lol.csv', 'g')
doPlot('data/2348dot8652lol.csv', 'r')



p3.setLabel('left', "speed")
p3.setLabel('bottom', "dist")

# do a cool line fit
#A = np.vstack([s1, s2, s3]).T # 
#A = np.vstack([x1, x2, np.ones((speed.shape[0]))]).T
#res = np.linalg.lstsq(A, accel, rcond=None)
#print(res[0])
#print(res)

#p3.addItem(curve)
#datasets = []
#for i in range(5):
#    datasets.append(loadSet('data/turnerror-00{}.csv'.format(i), rows))
#bolPoly = polyDataFor(bol)

#for data in datasets:
#    p3.plot(data, pen='g', symbolBrush=None, symbolPen=None)
#p3.plot(bolPoly, pen='w', symbolBrush=None, symbolPen=None)

def getDude(start):
    return (driftyBoi[start+1,1] - driftyBoi[start, 1]) / (driftyBoi[start, 1] * (driftyBoi[start + 1, 0] - driftyBoi[start, 0]))

## Start Qt event loop unless running in interactive mode or using pyside.
if __name__ == '__main__':
    import sys
    if (sys.flags.interactive != 1) or not hasattr(QtCore, 'PYQT_VERSION'):
        QtGui.QApplication.instance().exec_()

#code.interact(local=locals())