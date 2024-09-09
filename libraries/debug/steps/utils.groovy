package libraries.git.steps

void call() {
    //def source_branch = git_distributions.fetch().get_source_branch()
    node() {
    sh 'printenv'
    //println source_branch
    }
}
