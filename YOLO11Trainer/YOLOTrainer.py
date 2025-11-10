from ultralytics import YOLO
import os


def main():
    # Can use a pre-trained model to retrain
    model = YOLO('yolo11s.pt')
    
    # Move model to GPU
    model.to('cuda')

    # Start training, will update hyperparameters later
    results = model.train(data="data.yaml", epochs=300, imgsz=640,batch=32)
    
    
    print(results)


if __name__ == '__main__':
    main()