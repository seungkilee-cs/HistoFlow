# Sprint 07: Head-to-Head Comparison (Quick)

| Head Option | Boundary Type | Probability Output | PCA Compatibility | Typical Strength | Typical Risk | Current Status |
|---|---|---|---|---|---|---|
| Logistic Regression | Linear | Native (`predict_proba`) | Optional (can be added) | Fast, stable baseline, easy to interpret | Underfits nonlinear structure | Implemented and available (`head_type="logistic"`) |
| Linear SVM | Linear max-margin | Calibrated wrapper required | Yes (`StandardScaler -> PCA -> LinearSVC`) | Good in high-dimensional spaces, strong margin behavior | No native probabilities, calibration cost | Implemented and available (`head_type="linear_svm"`) |
| RBF SVM | Nonlinear (kernel) | Calibrated wrapper required | Yes (`StandardScaler -> PCA -> SVC`) | Captures nonlinear class boundaries best | Slower training/inference, more tuning needed | Implemented and set as current default (`head_type="svm_rbf"`) |

## Current Training Default (sk-regression)

- `HEAD_TYPE = "svm_rbf"`
- `USE_PCA = True`
- `PCA_COMPONENTS = 128`
- `CALIBRATE = True`
- `CALIBRATION_METHOD = "sigmoid"`
- `CALIBRATION_CV = 3`

## Practical Readout

- If your data boundary is close to linear, Logistic/Linear SVM may match RBF with lower complexity.
- If nonlinear separation is present, RBF SVM + PCA is the strongest candidate among current heads.
- Calibration keeps thresholding and probability-based metrics consistent across all heads.
