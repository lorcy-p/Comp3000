from ultralytics import YOLO
import random


def main():
    yaml_path = "Comp-3001--4/data.yaml"

    model = YOLO("YOLO11Small.pt")
    print("epoch")
    print(model.ckpt.get('epoch'))
    metrics = model.val(data=yaml_path, imgsz=736)

    print(metrics.box.map)
    print(metrics.box.map50)
    print(metrics.box.mp)
    print(metrics.box.mr)

if __name__ == '__main__':
    main()