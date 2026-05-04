#from google.colab import files
from ultralytics import YOLO
#from google.colab import drive
from roboflow import Roboflow


def main():

  #replace when needed
  
  yaml_path = "Comp-3000--4/data.yaml"


  model = YOLO("yolo11n.pt")
  model.to('cuda')

  results = model.train(data=yaml_path, epochs=175, imgsz=736,batch=16)

  #results = model.tune(data="data.yaml", epochs=80, imgsz=320,batch=64)

  #model.export(format='tflite',imgsz=736,int8=True,data=yaml_path)


if __name__ == '__main__':
  main()
