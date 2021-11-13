
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

p3 = win.addPlot(title="Drawing with points")

names = ["t", "accel", "pred"]
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

ang = loadSet('data/turntime.csv', [0])
speed = loadSet('data/turntime.csv', [1])[:-1]
accel = loadSet('data/turntime.csv', [2])[1:]
time = loadSet('data/turntime.csv', [3])
throttleAccel = loadSet('data/turntime.csv', [4])
maxTurnCurvature = loadSet('data/turntime.csv', [5])
steer = loadSet('data/turntime.csv', [6])

x1 = np.empty((speed.shape[0]))
x2 = np.empty_like(x1)
s1 = np.empty((speed.shape[0]))
s2 = np.empty_like(s1)
s3 = np.empty_like(s1)
pred_accel = np.empty_like(speed)
corrected_accel = np.empty_like(speed)
for i in range(pred_accel.shape[0]):
    x1[i] = speed[i] * maxTurnCurvature[i]
    x2[i] = x1[i] * speed[i]
    pred_accel[i] = -36.34783 * x1[i] -0.09389 * x2[i] + 41.91971
    s1[i] = steer[i] * pred_accel[i]
    s2[i] = s1[i] * steer[i]
    s3[i] = s2[i] * steer[i]
    corrected_accel[i] = 0.01461 * s1[i] + 0.83 * s2[i] + 0.15 * s3[i]
  
    
#y,x = np.histogram(data, bins=np.linspace(np.min(data), np.max(data), 100))
#curve = pg.PlotCurveItem(x, y, stepMode=True, fillLevel=0, brush=(0, 0, 255, 80))
p3.plot(np.concatenate((speed, corrected_accel), axis=1), pen=None, symbolBrush=None, symbolPen='w')
#p3.plot(np.concatenate((speed, throttleAccel), axis=1), pen='w', symbolBrush=None, symbolPen=None)
p3.plot(np.concatenate((speed, accel), axis=1), pen=None, symbolBrush=None, symbolPen='g')

# do a cool line fit
A = np.vstack([s1, s2, s3]).T # 
#A = np.vstack([x1, x2, np.ones((speed.shape[0]))]).T
res = np.linalg.lstsq(A, accel, rcond=None)
print(res[0])
print(res)

#p3.addItem(curve)
#datasets = []
#for i in range(5):
#    datasets.append(loadSet('data/turnerror-00{}.csv'.format(i), rows))
#bolPoly = polyDataFor(bol)

#for data in datasets:
#    p3.plot(data, pen='g', symbolBrush=None, symbolPen=None)
#p3.plot(bolPoly, pen='w', symbolBrush=None, symbolPen=None)

p3.setLabel('left', "accel")
p3.setLabel('bottom', "speed")

def getDude(start):
    return (driftyBoi[start+1,1] - driftyBoi[start, 1]) / (driftyBoi[start, 1] * (driftyBoi[start + 1, 0] - driftyBoi[start, 0]))

## Start Qt event loop unless running in interactive mode or using pyside.
if __name__ == '__main__':
    import sys
    if (sys.flags.interactive != 1) or not hasattr(QtCore, 'PYQT_VERSION'):
        QtGui.QApplication.instance().exec_()

#code.interact(local=locals())