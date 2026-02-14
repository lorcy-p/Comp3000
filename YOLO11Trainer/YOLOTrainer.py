#from google.colab import files
from ultralytics import YOLO
#from google.colab import drive
from roboflow import Roboflow


def main():

  #replace when needed
  rf = Roboflow(api_key="")
  project = rf.workspace("lorcans-projects").project("comp-3001-inr2l")
  dataset = project.version(4).download("yolov11")
  yaml_path = "Comp-3001--4/data.yaml"


  model = YOLO("last.pt")
  model.to('cuda')

  results = model.train(data=yaml_path, epochs=100, imgsz=736,batch=24,resume=True)

  #results = model.tune(data="data.yaml", epochs=80, imgsz=320,batch=64)

  #model.export(format='tflite',imgsz=736,int8=True,data=yaml_path)


if __name__ == '__main__':
  main()
