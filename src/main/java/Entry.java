public record Entry(int term, Command command) {
    public boolean equals(Entry e){
        return this.term == e.term() && this.command.equals(e.command);
    }
}
