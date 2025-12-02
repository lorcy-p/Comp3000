from ultralytics import YOLO
import os


def main():
    # Can use a pre-trained model to retrain
    model = YOLO('yolo11n.pt')
    
    # Move model to GPU for faster training
    model.to('cuda')

    # Fine-tune the model, will take a while
    #model.tune(data="data.yaml", epochs=30, iterations=100, optimizer="AdamW",batch=32, imgsz=640)
    
    # Start training, will update hyperparameters later
    results = model.train(data="data.yaml", epochs=80, imgsz=640,batch=32)
    
    


if __name__ == '__main__':
    main()