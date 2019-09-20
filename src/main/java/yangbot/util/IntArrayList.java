package yangbot.util;

public class IntArrayList {

    private int[] arr;
    private int size;

    public IntArrayList(int size){
        arr = new int[size];
        this.size = 0;
    }

    public int[] getArray(){
        return arr;
    }

    public int getSize(){
        return size;
    }

    public void add(int i){
        //if(size + 1 > arr.length)
        //    throw new IndexOutOfBoundsException("Tried to add element while size is "+arr.length);

        arr[size] = i;
        size++;
    }

    public void clear(){
        size = 0;
    }

    public int get(int index){
        if(index >= size)
            throw new IndexOutOfBoundsException("Index "+index+" > "+size);

        return arr[index];
    }
}
