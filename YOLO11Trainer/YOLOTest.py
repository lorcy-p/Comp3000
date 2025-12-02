import cv2
from ultralytics import YOLO
import random

# Load the best model
model = YOLO("best.pt")  

# Open webcam
cap = cv2.VideoCapture(0)


while True:
    ret, frame = cap.read()
    
    
    results = model(frame)

    # For each basket detection
    for r in results:
        for box in r.boxes:
            conf = float(box.conf[0])
            if conf < 0.4:
                continue

            cls = int(box.cls[0])
            

            # Bounding box
            x1, y1, x2, y2 = map(int, box.xyxy[0])
            cv2.rectangle(frame, (x1, y1), (x2, y2), (255, 255, 0), 2)

            # Label
            label = f"{r.names[cls]} {conf:.2f}"
            cv2.putText(frame, label, (x1, max(20, y1 - 10)),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 0), 2)

    # Show frame
    cv2.imshow("YOLO ", frame)

    # Press 'q' to exit
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()
